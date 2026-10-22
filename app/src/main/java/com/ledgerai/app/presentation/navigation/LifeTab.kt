package com.ledgerai.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerai.app.presentation.components.LLoading
import com.ledgerai.app.presentation.components.LScreen
import com.ledgerai.app.presentation.screens.auth.AuthViewModel
import com.ledgerai.app.presentation.screens.household.HouseholdScreen
import com.ledgerai.app.presentation.screens.inventory.InventoryScreen
import com.ledgerai.app.presentation.screens.inventory.InventoryViewModel
import com.ledgerai.app.presentation.screens.people.PeopleScreen
import com.ledgerai.app.presentation.screens.wardrobe.WardrobeScreen
import dagger.hilt.android.EntryPointAccessors

/**
 * One Library place, full screen, with the caller's back action.
 * Pantry, wardrobe, people and household. No segment tabs.
 */
@Composable
fun LifePlace(
    seg: LifeSeg,
    onBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val auth: AuthViewModel = hiltViewModel()
    val user by auth.uiState.collectAsState()

    when (seg) {
        LifeSeg.Pantry -> {
            val repository = remember(appContext) {
                EntryPointAccessors.fromApplication(appContext, InventoryEntryPoint::class.java).inventoryRepository()
            }
            val viewModel: InventoryViewModel = viewModel { InventoryViewModel(repository) }
            InventoryScreen(viewModel = viewModel, onBack = onBack)
        }
        LifeSeg.Wardrobe -> {
            val repository = remember(appContext) {
                EntryPointAccessors.fromApplication(appContext, WardrobeEntryPoint::class.java).wardrobeRepository()
            }
            if (user.userId.isNotBlank()) {
                WardrobeScreen(userId = user.userId, repository = repository, onBack = onBack)
            } else {
                LScreen(title = "Wardrobe", onBack = onBack) { item { LLoading() } }
            }
        }
        LifeSeg.People -> {
            val entry = remember(appContext) {
                EntryPointAccessors.fromApplication(appContext, PeopleEntryPoint::class.java)
            }
            if (user.userId.isNotBlank()) {
                PeopleScreen(
                    userId = user.userId,
                    repository = entry.peopleRepository(),
                    interactions = entry.interactionRepository(),
                    onOpenPerson = { person -> onOpenPerson(person.id) },
                    onBack = onBack,
                )
            } else {
                LScreen(title = "People", onBack = onBack) { item { LLoading() } }
            }
        }
        LifeSeg.Home -> {
            val repository = remember(appContext) {
                EntryPointAccessors.fromApplication(appContext, HouseholdEntryPoint::class.java).householdRepository()
            }
            if (user.userId.isNotBlank()) {
                HouseholdScreen(userId = user.userId, repository = repository, onBack = onBack, selfName = user.displayName)
            } else {
                LScreen(title = "Household", onBack = onBack) { item { LLoading() } }
            }
        }
    }
}
