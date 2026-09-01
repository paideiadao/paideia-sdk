package im.paideia.common.contracts

import im.paideia.common.PaideiaTestSuite
import im.paideia.util.Env
import org.ergoplatform.appkit.BlockchainContext
import org.ergoplatform.appkit.impl.BlockchainContextImpl

/** A `Config` variant that forces the JAR-packaged code paths of [[PaideiaContract]]
  * (`resourcesOnFilesystem == false`) while still resolving to the very same
  * `ergoscript/Config/latest/Config.{es,json}` classpath resources that a normal,
  * filesystem-backed `Config` instance uses. `sourcePath` must be overridden alongside
  * `resourcesOnFilesystem`: the base implementation derives it from `getClass.
  * getSimpleName()`, which for a subclass would resolve to "ForcedPackagedConfig"
  * instead of "Config".
  */
class ForcedPackagedConfig(contractSignature: PaideiaContractSignature)
  extends Config(contractSignature) {

  override protected def resourcesOnFilesystem: Boolean = false

  override def sourcePath(extension: String): String =
    "ergoscript/Config/latest/Config" + extension

  /** Exposes the protected seam for assertions - the suite isn't a [[PaideiaContract]]
    * subclass, so it can't read `resourcesOnFilesystem` directly.
    */
  def forcedPackaged: Boolean = !resourcesOnFilesystem
}

/** Verifies that [[PaideiaContract]] loading behaves identically whether resources are
  * read from the filesystem (the normal dev-checkout path) or, as they would be inside a
  * packaged JAR, purely from the classpath. Every `latest` `.es` file ships a precompiled
  * sibling `.json`, so `Config` - a real contract instantiable without any transaction
  * machinery - exercises the "latest" branch of `contractTemplate` under both modes.
  */
class JarSafeTemplateSuite extends PaideiaTestSuite {

  test(
    "ergoScript, contractTemplate and ergoTreeHex agree between filesystem and packaged (JAR) modes"
  ) {
    val ergoClient = createMockedErgoClient(MockData(Nil, Nil))
    ergoClient.execute(new java.util.function.Function[BlockchainContext, Unit] {
      override def apply(_ctx: BlockchainContext): Unit = {
        val ctx = _ctx.asInstanceOf[BlockchainContextImpl]

        PaideiaTestSuite.init(ctx)

        val filesystemContract =
          Config(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))
        val packagedContract =
          new ForcedPackagedConfig(PaideiaContractSignature(daoKey = Env.paideiaDaoKey))

        assert(packagedContract.forcedPackaged)

        // (a) ergoScript is non-empty and identical to the filesystem-mode script - the
        // classpath stream and the filesystem file serve the same resource content.
        assert(packagedContract.ergoScript._1.nonEmpty)
        assert(packagedContract.ergoScript._1 == filesystemContract.ergoScript._1)

        // (b) contractTemplate loads successfully from the classpath .json without
        // touching the filesystem.
        assert(packagedContract.contractTemplate != null)
        assert(
          packagedContract.contractTemplate.toJsonString ==
            filesystemContract.contractTemplate.toJsonString
        )

        // (c) The real invariant: the compiled ErgoTree - and therefore the ergoTreeHex
        // used to match transaction outputs - is exactly the same regardless of how the
        // contract's resources were loaded.
        assert(packagedContract.ergoTreeHex == filesystemContract.ergoTreeHex)
      }
    })
  }
}
