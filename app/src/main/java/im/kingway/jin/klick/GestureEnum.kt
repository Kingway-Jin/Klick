package im.kingway.jin.klick

/**
 * Created by koder on 16-7-28.
 */
enum class GestureEnum private constructor(val type: String, val code: Long, val desc: Int, val shortDesc: Int) {
    SINGLE_TAP("S", 1, R.string.gesture_single_tap, R.string.gesture_single_tap),
    DOUBLE_TAP("B", 2, R.string.gesture_double_tap, R.string.gesture_double_tap),
    LONG_PRESS("L", 3, R.string.gesture_long_press, R.string.gesture_long_press),
    SLIP_IN("I", 4, R.string.gesture_slip_in, R.string.gesture_slip_in_short),
    SLIP_OUT("O", 5, R.string.gesture_slip_out, R.string.gesture_slip_out_short),
    SLIP_UP("U", 6, R.string.gesture_slip_up, R.string.gesture_slip_up_short),
    SLIP_DOWN("D", 7, R.string.gesture_slip_down, R.string.gesture_slip_down_short);


    companion object {

        fun getCode(type: String): Long {
            for (g in GestureEnum.values()) {
                if (g.type == type)
                    return g.code
            }
            return 0
        }

        fun getType(code: Long): String {
            for (g in GestureEnum.values()) {
                if (g.code == code)
                    return g.type
            }
            return ""
        }

        fun getDesc(code: Long): Int {
            for (g in GestureEnum.values()) {
                if (g.code == code)
                    return g.desc
            }
            return R.string.blank
        }

        fun getDesc(type: String): Int {
            for (g in GestureEnum.values()) {
                if (g.type == type)
                    return g.desc
            }
            return R.string.blank
        }

        fun getShortDesc(code: Long): Int {
            for (g in GestureEnum.values()) {
                if (g.code == code)
                    return g.shortDesc
            }
            return R.string.blank
        }

        fun getShortDesc(type: String): Int {
            for (g in GestureEnum.values()) {
                if (g.type == type)
                    return g.shortDesc
            }
            return R.string.blank
        }
    }
}
