package com.ledgerai.app.data.security

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ledgerai.app.domain.security.AppLockPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Why an unlock did not happen. */
enum class UnlockFailure {
    /** No fingerprint (or other strong biometric) is enrolled on the phone. */
    NOT_ENROLLED,
    /** The phone has no usable biometric hardware right now. */
    UNAVAILABLE,
    /** The user cancelled, or too many wrong tries. */
    CANCELLED,
    /** Fingerprints were added or removed, which invalidates the key on purpose. */
    BIOMETRICS_CHANGED,
    /** The secure hardware did not authorise the key. A hooked callback ends up here. */
    NOT_PROVEN,
}

/**
 * The app lock. A fingerprint is the only way in: no PIN, pattern or password fallback.
 *
 * The unlock is not a yes/no callback. A key in the phone's secure hardware is created so that every use
 * needs a fresh strong-biometric match, and it is invalidated when fingerprints change. The prompt is
 * bound to that key, and the app only counts the unlock after it has used the key once. Code that
 * fakes the "success" callback has no authorised key to use, so it stays locked.
 *
 * This keeps other people out of the app. It does not encrypt the stored data: someone with root
 * access to the phone can still read the app's files.
 */
object AppLock {
    private const val KEY_ALIAS = "ledgerai_app_lock_v1"
    private const val KEYSTORE = "AndroidKeyStore"

    private val policy = AppLockPolicy()
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Volatile private var prompting = false
    private var startedActivities = 0

    fun isUnlocked(): Boolean = policy.unlocked

    fun lock() {
        policy.lock()
        _unlocked.value = false
    }

    /** Call once from the Application. Locks on screen off and after time away from the app. */
    fun install(app: Application) {
        app.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) = lock()
            },
            IntentFilter(Intent.ACTION_SCREEN_OFF),
        )
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (startedActivities++ == 0) {
                    policy.backInApp(SystemClock.elapsedRealtime())
                    _unlocked.value = policy.unlocked
                }
            }

            override fun onActivityStopped(activity: Activity) {
                if (--startedActivities <= 0) {
                    startedActivities = 0
                    policy.leftApp(SystemClock.elapsedRealtime())
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** No screenshots, no recents thumbnail, and touches are ignored while another window covers this one. */
    fun hardenWindow(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        activity.window.decorView.filterTouchesWhenObscured = true
    }

    /**
     * First thing in an entry screen's onCreate, after super. Returns true when the screen must wait:
     * the fingerprint prompt is up, and the screen is rebuilt after it passes or closed if it does not.
     * Nothing from the intent is read or saved before then.
     */
    fun holdIfLocked(activity: FragmentActivity): Boolean {
        hardenWindow(activity)
        if (policy.unlocked) return false
        unlock(activity, onUnlocked = { activity.recreate() }, onFailed = { activity.finish() })
        return true
    }

    /**
     * Asks for the fingerprint. [onUnlocked] runs only after the secure key was used.
     * Only one prompt is shown at a time; a second call while one is up does nothing.
     */
    fun unlock(
        activity: FragmentActivity,
        title: String = "Unlock Ledger AI",
        onUnlocked: () -> Unit,
        onFailed: (UnlockFailure) -> Unit,
    ) {
        if (policy.unlocked) { onUnlocked(); return }
        if (prompting) { onFailed(UnlockFailure.CANCELLED); return }
        val can = BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        when (can) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> { onFailed(UnlockFailure.NOT_ENROLLED); return }
            else -> { onFailed(UnlockFailure.UNAVAILABLE); return }
        }
        val cipher = try {
            newCipher()
        } catch (e: KeyPermanentlyInvalidatedException) {
            onFailed(UnlockFailure.BIOMETRICS_CHANGED)
            return
        } catch (e: Exception) {
            onFailed(UnlockFailure.UNAVAILABLE)
            return
        }

        prompting = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    // Prove the secure hardware authorised the key: one real use of it.
                    val proven = runCatching {
                        val used = result.cryptoObject?.cipher ?: error("no cipher")
                        used.doFinal(ByteArray(16).also { SecureRandom().nextBytes(it) }).isNotEmpty()
                    }.getOrDefault(false)
                    if (proven) {
                        policy.unlock()
                        _unlocked.value = true
                        onUnlocked()
                    } else {
                        onFailed(UnlockFailure.NOT_PROVEN)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                    onFailed(UnlockFailure.CANCELLED)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Use your fingerprint")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /** After fingerprints changed: the old key is gone for good, so make a fresh one. */
    fun resetKey() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS) }
    }

    private fun newCipher(): Cipher {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: createKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    private fun createKey(): SecretKey {
        fun spec(strongBox: Boolean): KeyGenParameterSpec {
            val b = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Every use needs a fresh strong-biometric match (timeout 0).
                b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                @Suppress("DEPRECATION")
                b.setUserAuthenticationValidityDurationSeconds(-1)
            }
            if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) b.setIsStrongBoxBacked(true)
            return b.build()
        }
        fun generate(strongBox: Boolean): SecretKey =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec(strongBox)) }.generateKey()
        return try {
            generate(strongBox = true)
        } catch (e: Exception) {
            // No dedicated secure chip on this phone: the normal secure hardware still guards the key.
            generate(strongBox = false)
        }
    }
}
