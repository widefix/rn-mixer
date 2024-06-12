
import SwiftUI

struct AudioSlider: View {
    @Binding var value: Double
    let inRange: ClosedRange<Double>
    let step: Double
    let onEditingChanged: (Bool) -> Void

    @GestureState private var isDragging = false

    var body: some View {
        Slider(
            value: Binding(
                get: { value },
                set: { newValue in
                    value = newValue
                }
            ),
            in: inRange,
            step: step,
            onEditingChanged: onEditingChanged
        )
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($isDragging) { _, state, _ in
                    state = true
                }
        )
    }
}
