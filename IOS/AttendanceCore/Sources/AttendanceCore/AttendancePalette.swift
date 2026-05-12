#if os(iOS)
import SwiftUI

public enum AttendancePalette {
    public static var background: Color {
        Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? .black : .white
        })
    }

    public static var foreground: Color {
        Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? .white : .black
        })
    }

    public static var secondaryForeground: Color {
        Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark
                ? UIColor.white.withAlphaComponent(0.65)
                : UIColor.black.withAlphaComponent(0.55)
        })
    }

    public static var lowAttendance: Color {
        .red
    }

    public static func rowColor(for percentage: Double) -> Color {
        percentage < 70 ? lowAttendance : foreground
    }
}
#endif
