package com.matan.garage.data

/** Starter maintenance plans. Intervals are rough placeholders - adjust per the owner's manual. */
object Templates {
    data class Tpl(val name: String, val category: Category, val km: Int?, val months: Int?)

    private val motorcycle = listOf(
        Tpl("שמן + פילטר שמן", Category.ENGINE, 6000, 12),
        Tpl("ניקוי ושימון שרשרת", Category.DRIVETRAIN, 600, null),
        Tpl("שרשרת וגלגלי שיניים", Category.DRIVETRAIN, 25000, null),
        Tpl("פילטר אוויר", Category.ENGINE, 12000, 24),
        Tpl("מצתים", Category.ENGINE, 12000, 24),
        Tpl("נוזל בלמים", Category.BRAKES, null, 24),
        Tpl("רפידות בלמים קדמיות", Category.BRAKES, 15000, null),
        Tpl("רפידות בלמים אחוריות", Category.BRAKES, 15000, null),
        Tpl("צמיג קדמי", Category.TIRES, 15000, 60),
        Tpl("צמיג אחורי", Category.TIRES, 10000, 60),
        Tpl("נוזל קירור", Category.FLUIDS, null, 36),
        Tpl("בדיקת מרווחי שסתומים", Category.ENGINE, 24000, null),
        Tpl("מצבר", Category.ELECTRICAL, null, 48),
    )

    private val car = listOf(
        Tpl("שמן + פילטר שמן", Category.ENGINE, 10000, 12),
        Tpl("פילטר אוויר", Category.ENGINE, 20000, 24),
        Tpl("פילטר מזגן", Category.OTHER, null, 12),
        Tpl("מצתים", Category.ENGINE, 40000, 48),
        Tpl("נוזל בלמים", Category.BRAKES, null, 24),
        Tpl("רפידות בלמים", Category.BRAKES, 30000, null),
        Tpl("צמיגים", Category.TIRES, 40000, 60),
        Tpl("נוזל קירור", Category.FLUIDS, null, 48),
        Tpl("רצועת טיימינג", Category.ENGINE, 90000, 72),
        Tpl("מצבר", Category.ELECTRICAL, null, 48),
        Tpl("מגבים", Category.OTHER, null, 12),
    )

    fun forType(type: VehicleType) = if (type == VehicleType.MOTORCYCLE) motorcycle else car
}
