import SwiftUI

struct AudioVisualizer: View {
    @Binding var amplitudes: [Float]

    var body: some View {
        GeometryReader { geometry in
            HStack(spacing: 4) {
                ForEach(amplitudes.indices, id: \.self) { index in
                    BarView(amplitude: amplitudes[index], index: index)
                        .frame(width: (geometry.size.width - CGFloat(amplitudes.count - 1) * 4) / CGFloat(amplitudes.count))
                }
            }
            .frame(height: geometry.size.height)
        }
    }
}

struct BarView: View {
    var amplitude: Float
    var index: Int

    var body: some View {
        Rectangle()
            .fill(colorForIndex(index: index))
            .scaleEffect(y: CGFloat(amplitude), anchor: .bottom)
            .animation(.linear, value: amplitude)
    }

    func colorForIndex(index: Int) -> Color {
        let colors: [Color] = [.red, .orange, .yellow, .green, .blue, .purple, .pink, .gray, .indigo, .teal]
        return colors[index % colors.count]
    }
}
