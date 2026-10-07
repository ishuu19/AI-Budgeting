# BudgetAI — AI-Powered Voice-First Android Budgeting App

A feature-rich Android budgeting app powered by OpenRouter AI (Claude 3.7 Sonnet by default).

## Features

| Feature | Description |
|---|---|
| **Voice Widget** | Home screen widget with mic button — speak a transaction, AI parses it instantly |
| **AI Transactions** | Natural language transaction entry ("Spent $50 on groceries") |
| **Budget Categories** | Set monthly limits per category with % alerts and AI advice |
| **AI Forecast** | 3-month spending forecast with risk level and recommendations |
| **Debt Tracker** | Track money owed to/from friends with Google Calendar + 5 reminder tiers |
| **Analytics** | Pie charts, bar charts, income vs. expense trends, category breakdown |
| **Savings Goals** | Set goals with progress tracking and AI tips |
| **Bills Tracker** | Manage recurring bills and subscriptions |
| **AI Assistant** | Chat with BudgetAI about your finances |
| **Smart Notifications** | Budget alerts, debt reminders, bill due alerts |
| **Financial Health Score** | Personalized score based on savings rate, debt ratio, and budget adherence |

## Debt Reminder Schedule

When you add a debt with a due date, BudgetAI automatically:
1. Creates a **Google Calendar event** on the due date
2. Schedules **5 push notifications**:
   - 1 week before
   - 5 days before
   - 1 day before
   - 10 hours before
   - 30 minutes before

## Setup

### 1. Clone & Open in Android Studio

Open this folder in Android Studio (Hedgehog or newer, with Android SDK 34+).

### 2. Configure API Key

```bash
# Copy the example secrets file
cp .env.example secrets.properties

# Edit secrets.properties and fill in your OpenRouter API key
OPENROUTER_API_KEY=your_key_here
```

Get your free API key at: https://openrouter.ai/keys

### 3. AI Model

Default model: `anthropic/claude-3.7-sonnet` (best reasoning for financial analysis)

You can change this in `secrets.properties` or in the app's Settings screen.

### 4. Build & Run

```bash
./gradlew assembleDebug
```

Or press ▶ in Android Studio.

## Architecture

```
app/
├── data/
│   ├── local/          # Room database (entities, DAOs)
│   ├── remote/         # OpenRouter API (Retrofit)
│   ├── repository/     # Repository pattern
│   └── preferences/    # DataStore user preferences
├── domain/
│   └── model/          # Domain models
├── presentation/
│   ├── navigation/     # Compose Navigation
│   ├── screens/        # Screen composables + ViewModels
│   ├── components/     # Shared UI components
│   └── theme/          # Material 3 theme
├── service/            # VoiceRecordingService, CalendarService, NotificationService
├── widget/             # Glance widget (home screen)
├── worker/             # WorkManager tasks (debt reminders, budget checks)
└── di/                 # Hilt dependency injection
```

**Tech Stack:**
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: MVVM + Clean Architecture
- DI: Hilt
- Database: Room (SQLite)
- Networking: Retrofit + OkHttp → OpenRouter AI
- Background: WorkManager
- Widget: Glance (Compose-based home screen widget)
- Preferences: DataStore
- Charts: Vico

## Required Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | AI API calls |
| `RECORD_AUDIO` | Voice transaction entry |
| `READ/WRITE_CALENDAR` | Debt reminder calendar events |
| `POST_NOTIFICATIONS` | Budget alerts, reminders |
| `READ_CONTACTS` | Optional: auto-fill friend contacts |

## Project Notes

- **secrets.properties** is git-ignored — never commit it
- Build config injects secrets at compile time via `BuildConfig.*`
- All AI calls route through `AiRepository` → `OpenRouterService`
