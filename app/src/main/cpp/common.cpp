#include "common.h"

namespace flowgesture {

const char* GestureTypeName(GestureType type) {
    switch (type) {
        case GestureType::kTap:
            return "TAP";
        case GestureType::kSwipeUp:
            return "SWIPE_UP";
        case GestureType::kSwipeDown:
            return "SWIPE_DOWN";
        case GestureType::kSwipeLeft:
            return "SWIPE_LEFT";
        case GestureType::kSwipeRight:
            return "SWIPE_RIGHT";
        default:
            return "NONE";
    }
}

const char* GestureTypeKey(GestureType type) {
    switch (type) {
        case GestureType::kTap:
            return "tap";
        case GestureType::kSwipeUp:
            return "up";
        case GestureType::kSwipeDown:
            return "down";
        case GestureType::kSwipeLeft:
            return "left";
        case GestureType::kSwipeRight:
            return "right";
        default:
            return "none";
    }
}

}  // namespace flowgesture
