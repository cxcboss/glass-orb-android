package com.cxcboss.glassorb.overlay

enum class SwipeDecision {
    Collapse,
    Restore;

    companion object {
        fun decide(deltaYDp: Float, velocityYDpPerSecond: Float): SwipeDecision =
            if (deltaYDp <= -64f || velocityYDpPerSecond <= -800f) Collapse else Restore
    }
}
