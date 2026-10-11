package com.ledgerai.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ledgerai.app.presentation.components.LSegments
import com.ledgerai.app.presentation.components.LTabPage
import com.ledgerai.app.presentation.screens.auth.AuthViewModel
import com.ledgerai.app.presentation.screens.household.HouseholdScreen
import com.ledgerai.app.presentation.screens.inventory.InventoryScreen
import com.ledgerai.app.presentation.screens.inventory.InventoryViewModel
import com.ledgerai.app.presentation.screens.people.PeopleScreen
import com.ledgerai.app.presentation.screens.wardrobe.WardrobeScreen
import dagger.hilt.android.EntryPointAccessors

/**
 * Life tab: the things you own and the people around you, in one place.
 * Pantry (stock and shopping), Wardrobe, People (memories) and Home (shared living).
 * The Capture tab files photos into these.
 */
@Composable
fun LifeTab(
    seg: LifeSeg,
    onSeg: (LifeSeg) -> Unit,
    onOpenPerson: (String) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val auth: AuthViewModel = hiltViewModel()
    val user by auth.uiState.collectAsState()

    LTabPage(
        title = "Life",
        segments = { LSegments(LifeSeg.values().toList(), seg, { it.label }, onSeg) }
    ) {
        when (seg) {
            LifeSeg.Pantry -> {
                val repository = remember(appContext) {
                    EntryPointAccessors.fromApplication(appContext, InventoryEntryPoint::class.java).inventoryRepository()
                }
                val viewModel: InventoryViewModel = viewModel { InventoryViewModel(repository) }
                InventoryScreen(viewModel = viewModel, onBack = {})
            }
            LifeSeg.Wardrobe -> {
                val repository = remember(appContext) {
                    EntryPointAccessors.fromApplication(appContext, WardrobeEntryPoint::class.java).wardrobeRepository()
                }
                WardrobeScreen(userId = user.userId, repository = repository, onBack = {})
            }
            LifeSeg.People -> {
                val repository = remember(appContext) {
                    EntryPointAccessors.fromApplication(appContext, PeopleEntryPoint::class.java).peopleRepository()
                }
                PeopleScreen(
                    userId = user.userId,
                    repository = repository,
                    onOpenPerson = { person -> onOpenPerson(person.id) },
                    onBack = {},
                )
            }
            LifeSeg.Home -> {
                val repository = remember(appContext) {
                    EntryPointAccessors.fromApplication(appContext, HouseholdEntryPoint::class.java).householdRepository()
                }
                HouseholdScreen(userId = user.userId, repository = repository, onBack = {})
            }
        }
    }
}
