# paideia-sdk
lorem ipsum

## Config value serialization
To allow for maximum flexibility in the config values stored for a dao and virtually no upper limit to the amount of them they are serialized into byte arrays and stored in an avl tree.
The off chain code handles the serialization and deserialization mostly automatically, giving you the original values to work with. Sadly this is not possible in ergoscript so to use the values inside a contract knowledge is needed on how the values are serialized.

### General rule
A serialized config value will start with one byte denoting the data type (see below for a table). If the data type is static length the following bytes will be the actual value, if it is variable length, the size will be defined by the first 4 bytes (32 bit int) following the datatype byte.

### Data types
| JVM data type | Byte value | Size bytes | Serialization method |
| --- | --- | --- | --- |
| Byte | 0 | 0 | 1:1 |
| Short | 1 | 0 | com.google.common.primitives.Shorts |
| Int | 2 | 0 | com.google.common.primitives.Ints |
| Long | 3 | 0 | com.google.common.primitives.Longs |
| BigInt | 4 | 0 | BigInt.toByteArray |
| Boolean | 5 | 0 | True = 1 False = 0
| String | 6 | 4 | String.getBytes(StandardCharsets.UTF_8) |
| PaideiaContractSignature | 7 | 0 | 32 bytes blake2b256 hash of contract ++ String serialized class name ++ String serialized version ++ networkPrefix byte |
| Array | 10 | 4* | Inner type byte ++ size bytes ++ data |
| Tuple | 20 | 0 | Serialized first entry ++ serialized second entry |

\* Note that the size bytes for an array are prepended with the inner data type
