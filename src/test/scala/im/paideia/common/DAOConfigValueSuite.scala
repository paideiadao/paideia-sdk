package im.paideia.common

import org.scalatest.funsuite.AnyFunSuite
import special.collection.Coll
import org.ergoplatform.sdk.ErgoId
import special.collection.CollOverArray
import im.paideia.DAOConfigValueSerializer
import im.paideia.DAOConfigValueDeserializer
import im.paideia.common.contracts.PaideiaContractSignature
import org.ergoplatform.appkit.NetworkType

class DAOConfigValueSuite extends AnyFunSuite {
  test("Byte -> Bytes -> Byte") {
    val b: Byte                 = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized: Byte      = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Byte")
  }

  test("Byte -> Bytes -> String") {
    val b: Byte                 = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert("10" === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("Short -> Bytes -> Short") {
    val s: Short                = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(s)
    val deserialized: Short     = DAOConfigValueDeserializer.deserialize(serialized)
    assert(s === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Short")
  }

  test("Short -> Bytes -> String") {
    val s: Short                = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(s)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert("10" === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("Int -> Bytes -> Int") {
    val i: Int                  = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(i)
    val deserialized: Int       = DAOConfigValueDeserializer.deserialize(serialized)
    assert(i === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Int")
  }

  test("Int -> Bytes -> String") {
    val i: Int                  = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(i)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert("10" === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("Long -> Bytes -> Long") {
    val l: Long                 = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(l)
    val deserialized: Long      = DAOConfigValueDeserializer.deserialize(serialized)
    assert(l === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Long")
  }

  test("Long -> Bytes -> String") {
    val l: Long                 = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(l)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert("10" === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("BigInt -> Bytes -> BigInt") {
    val bi: BigInt              = BigInt(10)
    val serialized: Array[Byte] = DAOConfigValueSerializer(bi)
    val deserialized: BigInt    = DAOConfigValueDeserializer.deserialize(serialized)
    assert(bi === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "BigInt")
  }

  test("BigInt -> Bytes -> String") {
    val bi: BigInt              = BigInt(10)
    val serialized: Array[Byte] = DAOConfigValueSerializer(bi)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert("10" === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("true -> Bytes -> true") {
    val b: Boolean              = true
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized: Boolean   = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Boolean")
  }

  test("true -> Bytes -> String") {
    val b: Boolean              = true
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert("true" === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("false -> Bytes -> false") {
    val b: Boolean              = false
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized: Boolean   = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Boolean")
  }

  test("false -> Bytes -> String") {
    val b: Boolean              = false
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert("false" === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("String -> Bytes -> String") {
    val s: String =
      """ăѣ𝔠ծềſģȟᎥ𝒋ǩľḿꞑȯ𝘱𝑞𝗋𝘴ȶ𝞄𝜈ψ𝒙𝘆𝚣1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝘈Ḇ𝖢𝕯٤ḞԍНǏ𝙅ƘԸⲘ𝙉০Ρ𝗤Ɍ𝓢ȚЦ𝒱Ѡ𝓧ƳȤѧᖯć𝗱ễ𝑓𝙜Ⴙ𝞲𝑗𝒌ļṃŉо𝞎𝒒ᵲꜱ𝙩ừ𝗏ŵ𝒙𝒚ź1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~АḂⲤ𝗗𝖤𝗙ꞠꓧȊ𝐉𝜥ꓡ𝑀𝑵Ǭ𝙿𝑄Ŗ𝑆𝒯𝖴𝘝𝘞ꓫŸ𝜡ả𝘢ƀ𝖼ḋếᵮℊ𝙝Ꭵ𝕛кιṃդⱺ𝓅𝘲𝕣𝖘ŧ𝑢ṽẉ𝘅ყž1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~Ѧ𝙱ƇᗞΣℱԍҤ١𝔍К𝓛𝓜ƝȎ𝚸𝑄Ṛ𝓢ṮṺƲᏔꓫ𝚈𝚭𝜶Ꮟçძ𝑒𝖿𝗀ḧ𝗂𝐣ҝɭḿ𝕟𝐨𝝔𝕢ṛ𝓼тú𝔳ẃ⤬𝝲𝗓1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝖠Β𝒞𝘋𝙴𝓕ĢȞỈ𝕵ꓗʟ𝙼ℕ০𝚸𝗤ՀꓢṰǓⅤ𝔚Ⲭ𝑌𝙕𝘢𝕤"""
    val serialized: Array[Byte] = DAOConfigValueSerializer(s)
    val deserialized: String    = DAOConfigValueDeserializer.deserialize(serialized)
    assert(s === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "String")
  }

  test("String -> Bytes -> toString") {
    val s: String =
      """ăѣ𝔠ծềſģȟᎥ𝒋ǩľḿꞑȯ𝘱𝑞𝗋𝘴ȶ𝞄𝜈ψ𝒙𝘆𝚣1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝘈Ḇ𝖢𝕯٤ḞԍНǏ𝙅ƘԸⲘ𝙉০Ρ𝗤Ɍ𝓢ȚЦ𝒱Ѡ𝓧ƳȤѧᖯć𝗱ễ𝑓𝙜Ⴙ𝞲𝑗𝒌ļṃŉо𝞎𝒒ᵲꜱ𝙩ừ𝗏ŵ𝒙𝒚ź1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~АḂⲤ𝗗𝖤𝗙ꞠꓧȊ𝐉𝜥ꓡ𝑀𝑵Ǭ𝙿𝑄Ŗ𝑆𝒯𝖴𝘝𝘞ꓫŸ𝜡ả𝘢ƀ𝖼ḋếᵮℊ𝙝Ꭵ𝕛кιṃդⱺ𝓅𝘲𝕣𝖘ŧ𝑢ṽẉ𝘅ყž1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~Ѧ𝙱ƇᗞΣℱԍҤ١𝔍К𝓛𝓜ƝȎ𝚸𝑄Ṛ𝓢ṮṺƲᏔꓫ𝚈𝚭𝜶Ꮟçძ𝑒𝖿𝗀ḧ𝗂𝐣ҝɭḿ𝕟𝐨𝝔𝕢ṛ𝓼тú𝔳ẃ⤬𝝲𝗓1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝖠Β𝒞𝘋𝙴𝓕ĢȞỈ𝕵ꓗʟ𝙼ℕ০𝚸𝗤ՀꓢṰǓⅤ𝔚Ⲭ𝑌𝙕𝘢𝕤"""
    val serialized: Array[Byte] = DAOConfigValueSerializer(s)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert(s === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("Array[Byte] -> Bytes -> Array[Byte]") {
    val b: Array[Byte]              = Array(10.toByte, 20.toByte, 30.toByte)
    val serialized: Array[Byte]     = DAOConfigValueSerializer(b)
    val deserialized: Array[Object] = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Coll[Byte]")
  }

  test("Array[Byte] -> Bytes -> String") {
    val b: Array[Byte] = ErgoId
      .create("1fd6e032e8476c4aa54c18c1a308dce83940e8f4a28f576440513ed7326ad489")
      .getBytes
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert("1fd6e032e8476c4aa54c18c1a308dce83940e8f4a28f576440513ed7326ad489" === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("Array[Long] -> Bytes -> String") {
    val b: Array[Long]          = Array(10L, 20L, 30L)
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert("[10,20,30]" === string)
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("Empty Array[Byte] -> Bytes -> Array[Byte]") {
    val b: Array[Byte]              = new Array(0)
    val serialized: Array[Byte]     = DAOConfigValueSerializer(b)
    val deserialized: Array[Object] = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Coll[Byte]")
  }

  test("Array[Array[Byte]] -> Bytes -> String") {
    val b: Array[Array[Byte]] = Array(
      ErgoId
        .create("1fd6e032e8476c4aa54c18c1a308dce83940e8f4a28f576440513ed7326ad489")
        .getBytes,
      ErgoId
        .create("1fd6e032e8476c4aa54c18c1a308dce83940e8f4a28f576440513ed7326ad479")
        .getBytes,
      ErgoId
        .create("1fd6e032e8476c4aa54c18c1a308dce83940e8f4a28f576440513ed7326ad469")
        .getBytes
    )
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert(
      """["1fd6e032e8476c4aa54c18c1a308dce83940e8f4a28f576440513ed7326ad489","1fd6e032e8476c4aa54c18c1a308dce83940e8f4a28f576440513ed7326ad479","1fd6e032e8476c4aa54c18c1a308dce83940e8f4a28f576440513ed7326ad469"]""" === string
    )
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("Array[Array[Byte]] -> Bytes -> Array[Array[Byte]]") {
    val b: Array[Array[Byte]] = Array(
      Array(10.toByte, 20.toByte, 30.toByte),
      Array(10.toByte, 20.toByte, 30.toByte),
      Array(10.toByte, 20.toByte, 30.toByte)
    )
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized: Array[Object] =
      DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Coll[Coll[Byte]]")
  }

  test("Empty Array[Array[Byte]] -> Bytes -> Array[Array[?]]") {
    val b: Array[Array[Byte]]   = new Array(0)
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized: Array[Object] =
      DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "Coll[Coll[?]]")
  }

  test("(Int,String) -> Bytes -> (Int,String)") {
    val s: String =
      """ăѣ𝔠ծềſģȟᎥ𝒋ǩľḿꞑȯ𝘱𝑞𝗋𝘴ȶ𝞄𝜈ψ𝒙𝘆𝚣1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝘈Ḇ𝖢𝕯٤ḞԍНǏ𝙅ƘԸⲘ𝙉০Ρ𝗤Ɍ𝓢ȚЦ𝒱Ѡ𝓧ƳȤѧᖯć𝗱ễ𝑓𝙜Ⴙ𝞲𝑗𝒌ļṃŉо𝞎𝒒ᵲꜱ𝙩ừ𝗏ŵ𝒙𝒚ź1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~АḂⲤ𝗗𝖤𝗙ꞠꓧȊ𝐉𝜥ꓡ𝑀𝑵Ǭ𝙿𝑄Ŗ𝑆𝒯𝖴𝘝𝘞ꓫŸ𝜡ả𝘢ƀ𝖼ḋếᵮℊ𝙝Ꭵ𝕛кιṃդⱺ𝓅𝘲𝕣𝖘ŧ𝑢ṽẉ𝘅ყž1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~Ѧ𝙱ƇᗞΣℱԍҤ١𝔍К𝓛𝓜ƝȎ𝚸𝑄Ṛ𝓢ṮṺƲᏔꓫ𝚈𝚭𝜶Ꮟçძ𝑒𝖿𝗀ḧ𝗂𝐣ҝɭḿ𝕟𝐨𝝔𝕢ṛ𝓼тú𝔳ẃ⤬𝝲𝗓1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝖠Β𝒞𝘋𝙴𝓕ĢȞỈ𝕵ꓗʟ𝙼ℕ০𝚸𝗤ՀꓢṰǓⅤ𝔚Ⲭ𝑌𝙕𝘢𝕤"""
    val i: Int                  = 10
    val t: (Int, String)        = (i, s)
    val serialized: Array[Byte] = DAOConfigValueSerializer(t)
    val deserialized: Any =
      DAOConfigValueDeserializer.deserialize(serialized)
    assert(t === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "(Int,String)")
  }

  test("(Int,String) -> Bytes -> String") {
    val s: String =
      """ăѣ𝔠ծềſģȟᎥ𝒋ǩľḿꞑȯ𝘱𝑞𝗋𝘴ȶ𝞄𝜈ψ𝒙𝘆𝚣1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝘈Ḇ𝖢𝕯٤ḞԍНǏ𝙅ƘԸⲘ𝙉০Ρ𝗤Ɍ𝓢ȚЦ𝒱Ѡ𝓧ƳȤѧᖯć𝗱ễ𝑓𝙜Ⴙ𝞲𝑗𝒌ļṃŉо𝞎𝒒ᵲꜱ𝙩ừ𝗏ŵ𝒙𝒚ź1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~АḂⲤ𝗗𝖤𝗙ꞠꓧȊ𝐉𝜥ꓡ𝑀𝑵Ǭ𝙿𝑄Ŗ𝑆𝒯𝖴𝘝𝘞ꓫŸ𝜡ả𝘢ƀ𝖼ḋếᵮℊ𝙝Ꭵ𝕛кιṃդⱺ𝓅𝘲𝕣𝖘ŧ𝑢ṽẉ𝘅ყž1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~Ѧ𝙱ƇᗞΣℱԍҤ١𝔍К𝓛𝓜ƝȎ𝚸𝑄Ṛ𝓢ṮṺƲᏔꓫ𝚈𝚭𝜶Ꮟçძ𝑒𝖿𝗀ḧ𝗂𝐣ҝɭḿ𝕟𝐨𝝔𝕢ṛ𝓼тú𝔳ẃ⤬𝝲𝗓1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝖠Β𝒞𝘋𝙴𝓕ĢȞỈ𝕵ꓗʟ𝙼ℕ০𝚸𝗤ՀꓢṰǓⅤ𝔚Ⲭ𝑌𝙕𝘢𝕤"""
    val i: Int                  = 10
    val t                       = (i, s)
    val serialized: Array[Byte] = DAOConfigValueSerializer(t)
    val string                  = DAOConfigValueDeserializer.toString(serialized)
    assert(
      "(" + i.toString + ""","""" + s.replace(""""""", """""""") + """")""" === string
    )
    val reSerialized = DAOConfigValueSerializer.fromString(
      DAOConfigValueDeserializer.getType(serialized),
      string
    )
    assert(serialized === reSerialized)
  }

  test("PaideiaContractSignature -> Bytes -> PaideiaContractSignature") {
    val pcs = PaideiaContractSignature(
      "dummy.test",
      "finalv2.0",
      NetworkType.MAINNET,
      List(
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte
      )
    )
    val serialized = DAOConfigValueSerializer(pcs)
    val deserialized: PaideiaContractSignature =
      DAOConfigValueDeserializer.deserialize(serialized)
    assert(pcs === deserialized)
    val valueType = DAOConfigValueDeserializer.getType(serialized)
    assert(valueType === "PaideiaContractSignature")
  }

  test("PaideiaContractSignature -> Bytes -> String") {
    val pcs = PaideiaContractSignature(
      "dummy.test",
      "finalv2.0",
      NetworkType.MAINNET,
      List(
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte,
        1.toByte,
        3.toByte,
        3.toByte,
        7.toByte
      )
    )
    val serialized = DAOConfigValueSerializer(pcs)
    val string     = DAOConfigValueDeserializer.toString(serialized)
    assert(
      "PaideiaContractSignature(dummy.test,finalv2.0,MAINNET,0103030701030307010303070103030701030307010303070103030701030307)" === string
    )
  }
}
