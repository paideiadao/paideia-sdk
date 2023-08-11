package im.paideia.common

import org.scalatest.funsuite.AnyFunSuite
import im.paideia.DAOConfigValue
import special.collection.Coll
import org.ergoplatform.sdk.ErgoId
import special.collection.CollOverArray
import im.paideia.DAOConfigValueSerializer
import im.paideia.DAOConfigValueDeserializer

class DAOConfigValueSuite extends AnyFunSuite {
  test("Byte -> Bytes -> Byte") {
    val b: Byte                 = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
  }

  test("Short -> Bytes -> Short") {
    val s: Short                = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(s)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(s === deserialized)
  }

  test("Int -> Bytes -> Int") {
    val i: Int                  = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(i)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(i === deserialized)
  }

  test("Long -> Bytes -> Long") {
    val l: Long                 = 10
    val serialized: Array[Byte] = DAOConfigValueSerializer(l)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(l === deserialized)
  }

  test("BigInt -> Bytes -> BigInt") {
    val bi: BigInt              = BigInt(10)
    val serialized: Array[Byte] = DAOConfigValueSerializer(bi)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(bi === deserialized)
  }

  test("true -> Bytes -> true") {
    val b: Boolean              = true
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
  }

  test("false -> Bytes -> false") {
    val b: Boolean              = false
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
  }

  test("String -> Bytes -> String") {
    val s: String =
      """ăѣ𝔠ծềſģȟᎥ𝒋ǩľḿꞑȯ𝘱𝑞𝗋𝘴ȶ𝞄𝜈ψ𝒙𝘆𝚣1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝘈Ḇ𝖢𝕯٤ḞԍНǏ𝙅ƘԸⲘ𝙉০Ρ𝗤Ɍ𝓢ȚЦ𝒱Ѡ𝓧ƳȤѧᖯć𝗱ễ𝑓𝙜Ⴙ𝞲𝑗𝒌ļṃŉо𝞎𝒒ᵲꜱ𝙩ừ𝗏ŵ𝒙𝒚ź1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~АḂⲤ𝗗𝖤𝗙ꞠꓧȊ𝐉𝜥ꓡ𝑀𝑵Ǭ𝙿𝑄Ŗ𝑆𝒯𝖴𝘝𝘞ꓫŸ𝜡ả𝘢ƀ𝖼ḋếᵮℊ𝙝Ꭵ𝕛кιṃդⱺ𝓅𝘲𝕣𝖘ŧ𝑢ṽẉ𝘅ყž1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~Ѧ𝙱ƇᗞΣℱԍҤ١𝔍К𝓛𝓜ƝȎ𝚸𝑄Ṛ𝓢ṮṺƲᏔꓫ𝚈𝚭𝜶Ꮟçძ𝑒𝖿𝗀ḧ𝗂𝐣ҝɭḿ𝕟𝐨𝝔𝕢ṛ𝓼тú𝔳ẃ⤬𝝲𝗓1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝖠Β𝒞𝘋𝙴𝓕ĢȞỈ𝕵ꓗʟ𝙼ℕ০𝚸𝗤ՀꓢṰǓⅤ𝔚Ⲭ𝑌𝙕𝘢𝕤"""
    val serialized: Array[Byte] = DAOConfigValueSerializer(s)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(s === deserialized)
  }

  test("Array[Byte] -> Bytes -> Array[Byte]") {
    val b: Array[Byte]          = Array(10.toByte, 20.toByte, 30.toByte)
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
  }

  test("Array[Array[Byte]] -> Bytes -> Array[Array[Byte]]") {
    val b: Array[Array[Byte]] = Array(
      Array(10.toByte, 20.toByte, 30.toByte),
      Array(10.toByte, 20.toByte, 30.toByte),
      Array(10.toByte, 20.toByte, 30.toByte)
    )
    val serialized: Array[Byte] = DAOConfigValueSerializer(b)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(b === deserialized)
  }

  test("(Int,String) -> Bytes -> (Int,String)") {
    val s: String =
      """ăѣ𝔠ծềſģȟᎥ𝒋ǩľḿꞑȯ𝘱𝑞𝗋𝘴ȶ𝞄𝜈ψ𝒙𝘆𝚣1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝘈Ḇ𝖢𝕯٤ḞԍНǏ𝙅ƘԸⲘ𝙉০Ρ𝗤Ɍ𝓢ȚЦ𝒱Ѡ𝓧ƳȤѧᖯć𝗱ễ𝑓𝙜Ⴙ𝞲𝑗𝒌ļṃŉо𝞎𝒒ᵲꜱ𝙩ừ𝗏ŵ𝒙𝒚ź1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~АḂⲤ𝗗𝖤𝗙ꞠꓧȊ𝐉𝜥ꓡ𝑀𝑵Ǭ𝙿𝑄Ŗ𝑆𝒯𝖴𝘝𝘞ꓫŸ𝜡ả𝘢ƀ𝖼ḋếᵮℊ𝙝Ꭵ𝕛кιṃդⱺ𝓅𝘲𝕣𝖘ŧ𝑢ṽẉ𝘅ყž1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~Ѧ𝙱ƇᗞΣℱԍҤ١𝔍К𝓛𝓜ƝȎ𝚸𝑄Ṛ𝓢ṮṺƲᏔꓫ𝚈𝚭𝜶Ꮟçძ𝑒𝖿𝗀ḧ𝗂𝐣ҝɭḿ𝕟𝐨𝝔𝕢ṛ𝓼тú𝔳ẃ⤬𝝲𝗓1234567890!@#$%^&*()-_=+[{]};:'",<.>/?~𝖠Β𝒞𝘋𝙴𝓕ĢȞỈ𝕵ꓗʟ𝙼ℕ০𝚸𝗤ՀꓢṰǓⅤ𝔚Ⲭ𝑌𝙕𝘢𝕤"""
    val i: Int                  = 10
    val t                       = (s, i)
    val serialized: Array[Byte] = DAOConfigValueSerializer(t)
    val deserialized            = DAOConfigValueDeserializer.deserialize(serialized)
    assert(t === deserialized)
  }
}
