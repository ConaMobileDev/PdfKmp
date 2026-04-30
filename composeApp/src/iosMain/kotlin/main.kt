import androidx.compose.ui.window.ComposeUIViewController
import com.conamobile.romchi.App
import com.conamobile.romchi.mapController.MapControllerHolder
import platform.UIKit.UIViewController

fun MainViewController(
    mapUIViewController: () -> UIViewController
) = ComposeUIViewController {
    MapControllerHolder.factory = mapUIViewController
    App()
}