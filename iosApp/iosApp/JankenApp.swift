import SwiftUI
import UIKit
import JankenShared

struct GameView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(cameraFactory: { sink in CameraViewController(sink: sink) })
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct JankenApp: App {
    var body: some Scene {
        WindowGroup { GameView().ignoresSafeArea() }
    }
}
