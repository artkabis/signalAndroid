package com.samsung.remote.model

enum class RemoteKey(val keyCode: String) {
    // Power and Menu
    KEY_POWER("KEY_POWER"),
    KEY_POWEROFF("KEY_POWEROFF"),
    KEY_SOURCE("KEY_SOURCE"),
    KEY_MENU("KEY_MENU"),
    KEY_TOOLS("KEY_TOOLS"),
    KEY_INFO("KEY_INFO"),
    KEY_HOME("KEY_HOME"),
    KEY_RETURN("KEY_RETURN"),

    // Navigation
    KEY_UP("KEY_UP"),
    KEY_DOWN("KEY_DOWN"),
    KEY_LEFT("KEY_LEFT"),
    KEY_RIGHT("KEY_RIGHT"),
    KEY_ENTER("KEY_ENTER"),

    // Volume
    KEY_VOLUP("KEY_VOLUP"),
    KEY_VOLDOWN("KEY_VOLDOWN"),
    KEY_MUTE("KEY_MUTE"),

    // Channel
    KEY_CHUP("KEY_CHUP"),
    KEY_CHDOWN("KEY_CHDOWN"),

    // Media Controls
    KEY_PLAY("KEY_PLAY"),
    KEY_PAUSE("KEY_PAUSE"),
    KEY_STOP("KEY_STOP"),
    KEY_REWIND("KEY_REWIND"),
    KEY_FF("KEY_FF"),

    // Numbers
    KEY_0("KEY_0"),
    KEY_1("KEY_1"),
    KEY_2("KEY_2"),
    KEY_3("KEY_3"),
    KEY_4("KEY_4"),
    KEY_5("KEY_5"),
    KEY_6("KEY_6"),
    KEY_7("KEY_7"),
    KEY_8("KEY_8"),
    KEY_9("KEY_9")
}
