package com.tangnb.superaar

object LogUtil {

  private const val ANSI_RESET = "\u001B[0m"
  private const val ANSI_RED = "\u001B[31m"
  private const val ANSI_GREEN = "\u001B[32m"
  private const val ANSI_YELLOW = "\u001B[33m"
  private const val ANSI_BLUE = "\u001B[34m"

  @JvmStatic
  fun green(text: String) {
    println(ANSI_GREEN + text + ANSI_RESET)
  }

  @JvmStatic
  fun yellow(text: String) {
    println(ANSI_YELLOW + text + ANSI_RESET)
  }

  @JvmStatic
  fun blue(text: String) {
    println(ANSI_BLUE + text + ANSI_RESET)
  }

  @JvmStatic
  fun error(msg: Any) {
    println(ANSI_RED + msg + ANSI_RESET)
  }

  @JvmStatic
  fun info(msg: String) {
    println(msg)
  }
}

fun String.gradleVersionBiggerOrEqualThan(version2: String): Boolean {
  var a = this.replace(".", "").toInt()
  if (a < 100) {// deal with 3.4 == 34
    a *= 10
  }

  var b = version2.replace(".", "").toInt()
  if (b < 100) {// deal with 3.4 == 34
    b *= 10
  }
  return a >= b
}

fun String.gradleVersionLitterThan(version2: String): Boolean {
  return !this.gradleVersionBiggerOrEqualThan(version2)
}

fun main() {
  println("3.5.0".gradleVersionBiggerOrEqualThan("3.4.0"))//true
  println("3.5.0".gradleVersionBiggerOrEqualThan("3.4"))//true
  println("3.3.0".gradleVersionBiggerOrEqualThan("3.4"))//false
  println("3.3".gradleVersionBiggerOrEqualThan("3.4"))//false
  println("2.1.4".gradleVersionBiggerOrEqualThan("3.4"))//false
}