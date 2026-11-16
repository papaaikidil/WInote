package id.goresan.ui

import androidx.compose.runtime.Composable

@Composable
fun GoresanApp(state: AppState) {
    if (state.openDocId == null) GalleryScreen(state) else EditorScreen(state)
}
