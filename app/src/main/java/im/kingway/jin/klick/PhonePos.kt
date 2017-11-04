package im.kingway.jin.klick

class PhonePos(var pos: Int, var curTime: Long?) {
    var startTime: Long? = null

    init {
        this.startTime = curTime
    }

    fun timeLast(): Long? {
        return this.curTime!! - this.startTime!!
    }

}
