# Bubby app

Plain Kotlin Android app: the floating mascot service plus the settings UI. See the repository README for the layout and
build steps, and `docs/KOTLIN-MIGRATION.md` for how the UI was moved off Flutter.

Adding a screen: create `ui/XxxScreen.kt` (a `Screen` with `title()` and `render(c)`), register its name in
`ui/Screens.kt`, open it with `act.open("name")`. Strings go through `Tr.t(ctx, ru, en, uz)`; icons are vector drawables
in `res/drawable/ic_*.xml`.
