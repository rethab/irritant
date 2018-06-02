package com.irritant

/**
 * Defines how to interact with other people (eg. via slack).
 *
 * Since we don't want to bother people erroneously, the run
 * mode may be used for safety or testing purposes.
 */
sealed trait RunMode


case object RunMode {

  /** Don't trigger notifications, only print to stdout */
  case object Dry extends RunMode

  /** Ask before triggering notifications */
  case object Safe extends RunMode

  /** Send notifications w/o asking */
  case object Yolo extends RunMode

}
