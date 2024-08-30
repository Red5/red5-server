package org.red5.client.net.rtmp;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.apache.commons.codec.binary.Hex;
import org.apache.mina.core.buffer.IoBuffer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.red5.io.utils.IOUtils;
import org.red5.server.net.rtmp.RTMPConnection;
import org.red5.server.net.rtmp.RTMPHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RTMPHandshakeTest {

    private Logger log = LoggerFactory.getLogger(RTMPHandshakeTest.class);

    private IoBuffer clientC0C1;

    private IoBuffer clientC2;

    private IoBuffer serverS0S1S2part1;

    private IoBuffer serverS0S1S2part2;

    @SuppressWarnings("unused")
    private String youtubeC0C1 = "000000000a002d0235c46e929009dde8c80b089c3026c0938c1fa6415bff483969db1468d34f1c884457b87da48b1f17d2e39645da5a9c16af84ada92e69c5c4074e17098ff957ac1a1cf790ef1791055d45fb21c0c07c070cbc32cf0335a45c61a2034fd6c26207cf2cb43ae3dded6af8df61562548e7162f543174d6921349c7be44e484384aa181215342a64ce1c3d43a0114200a80f0e7b59ada97b9b85e6db722338d49b3f0d14c3a4a94d582aa4885f73c1d0fceecc6a8753dab512608eb26ddfe75f5944c630816d3bb7eb8364ef7e10b5c656263a4e7b9a76cafc5eebe1688c7d64a2a40621189025e45695003c2cf2571875b491cc559fea4ddbc90c4776df8deb5b5f2f9f4dba77350a94f2a907210c2127f8d0de02b8f16789b78c42af556ce9eefaddb8c7df925f349e2aa96aea00dce63d01ae8a63af555d8dae49855267c583a4fbb8b664e527f77f10c35ec97dfff65ac2b72a15a6b046a1431dc8efad25ef518d519b2a60de84e2dfe25cd501d1560beeadc87dc141104e51b7872434de589ea6481fd2e1d3495f7d23f270e7f9761054745baa18024c346f5a1b92e7eb50a5f29eb600a7dc0faa1ffc466391074defcf58a041db4ec517479196c9e8c5d81b3eb2a1397da5294579f44ba9fb091100aea646250a9d7d00543911997b1ae429ff19b0a1218fc7394f00aaa8792b78572e38581c2cc9d0a0c51a7a7b7b9dc8354004bf4c42857297d0270fa807af3e24b9621d232352a3c3514434f1de607af2385bfe0b3c54d446650e10dfb9031ad9b9d9d433e900012723382dd7f8ed3a226b96e10667ce670641010f07737f0cf79290d5c0acfc05c267bb843015bfe6e6f7f71f4f9a7eb8bdee2930a4d378988b84d6ef7717568ff54f05053a80a02f0ce32e432d7ab9b64bb36b20698e79643aa18b28585b4b1e88708c28c9acd22122eb6e5287624dfa835a5e03639f48ae71d5cf03c2630f0e9e89a10bab5691d31ffefe36e7a60dbf4886725d78eddc4f0e531fc4587824b1d3562b804f9b8254f0145f8e9d7e2545299db07c90674f889757208377bb7c9e88f8024b0c62935bdda2654fc40dfe151650311ade463e4da983e5c8ee3cf29689ef9b77a183ba90e6a84bda69cc4ec272e05dc7bb4a55807fb78171fdc3e8e47e33566e73e053272ed531bad8bae867784abb60004088d98414807a2c86df7588a7fdbf1c9b110ca490c121014f009b5d3e4d1d727b2c74a02998adc3e3a8e6eb28935efc7fda51dcc2db06d62918c4a994102d202995379bd98860fe474be43cbe54b9d9db9503eb9b72a1b31cb22e82f7810e00009c7a2543b6ba0b860217fcc8c88a2584b9cb9e0afa909cd8df1a9fae599e7b8229abf29b8472ef9373817e25b5000842ebf72319d24c8e0fa10fec6048123f3580d653109ef84b0a5f6a3a992fb39346e697dff104f3426e83e746ff8edf123f15feec66f2c20524c1d6743535e32372de313441795acdf6261e12408df31c58a5626ffec0731393f64154929e10112227bc2a1c927d58127d980461c49925a93112b8bc71a57b48775aa337e2947bd15452260bf19424728a4fd5c44c87457ce3386fa0cf47f5aa2b7d1d82352b4de6428bde3958b987f0e508d9f31e0458fd458b3500a8d9ec1fee7ad6fccb3da94823ef55f6ce8f84bb1bc8665bacc6c5390db8e6fd3cd2c54838509fc913d77c8728109a26b32c422a2ae1e0a59e234e4c2ec66cdbf21a9ee060b3a166f5dc15c50ca91dd395f0ee5a2313413ac7e52647df9a84ae91b8222d7f93fc5c5787e86c81f0c5485c44f8829cb56dff488e510a96165c0b02b227834b4ed87d8f4db079d54558a7f942757e07c70ba99821b1a827e230e38610dced3271fb94fcf510d24f626805c6a41942797bdfba427d31a17f9bf02e74143ff5c484c32b7c60feec68af406d5e6afda905bfcba439fe3d7cc704f79e8ac564fed59006f0013b7208220c2e6a85c4eef626ffebd483ce9a1d8713f68abcfedb6940b007bfead624b682bcef8168dfbd6171e067babcdb96ec8bb2f6e632a9c0fd86128a4e92b40741fb428c16474bfedde6b996f047fe281fad73d9d86a4484a31715040dd233bfe60d691595a262e6b19f8b4961149d1452300bde251b1914f8be8b7d6";

    private String youtubeS1 = "000000000400000126b33989118ea1422bc3c3317eb7a6774d6c0f5dafc8ea157885c76344adfe087437e66f8ce198081e6c6cc3a340c17a7ad8483a62534ed21f2894f4f6189787be97d83cffd2e98ebf787313885757746896d4cd1aefee23b32ccf7d36036ecf913dfa49930d69a725992ba9a5790254fcd7affbe902689ce7c94b5033f313090d30a78f79860e7bcf5882e52cd70d486d4f19cae1b56bdc8693d80c5b6e413fa8965b76b5acfaafe926c6ec2b95fbb5f4e680a883251a5bfe40fe47900be1929c4617803522fa2dcc349e9ea4d35f8d37222a0831ecde078854e5a8237ff2ee67b7a6326985e241de02fd1829c1dd3572af76d1df23ef4519a8463d4e69759ae1a350602c89473f3ace601da88174f92c3e267c7f84b82acee80df6d660dccff6fd4aa9c02ee28bc1df77566d93a1651fcf6c89bc5c9d1dc0b0e2cdaafe0a028b6f5a825a3751d596ef413abf198f9959869b4d906a70eb660e977e62e2b6519b28ca9431780875b6f5354d0adc0ac21eb9e2197433af49ac7cbb6b20eb5c21a2eb3c72159b4cdea534f324ecee0aec977aea152b043eaa1e5c03a147ac1cee8fa17e0536351d2e9e20c1c6f1bfa4d7aca02ef01a01aa5f68e82667646d074817a5b884a0021e0d54d8ea88fb6bcc618e1bff170fed08844cdc21dc077383d137b31a0a502313404423f4d1a39d29e0706e0e1d36c3a3eb10bddbf7cc1ec19d73bf7df5c8e310d06966465fb1a1c61e7fa476ec5615a75597f8c397443401bf82849af05ac48dd11d959a9708512827293f8d404913e779e31197c2f1e927dbc5f6585ba06297d975f4c2e0d5102430340de92d86a707fb096ba2d9bf2b02f4d5023f010be0b718b88b462af971ca7d0c59c0f5098e08ae6967bb169a5365a6be5a2974d43a287fcdff34bbb54471a8914d63cbb763d3ccf7a63974dc30affd3d10e1fc7ded4b138ab0ed8c539c2182d7f950c793be8150229e1a96803459bdaaad254179f361a407e10cb4a2fd2fcbd628ae363cbba7b27da583e95cc5e0b4d493f8b17258351adde40d2e8fce8cd0454ad49e3a128cc689f45ba714903f49733b7ead91a965a3ba65b4914139de79d29afb60fe97426c315d7fde490783e2922e3c20af94d7f2bd1e3403436d564462d8c3295e1e9db3972c574a0669312cad2576d37be5bfcf5ca9ae618c7f45fb78771e773f3c68afbd3f013356c6389f38c75f28d244c0431bc93ecd1ced40ceb880ddea7d93ac5efbad9ab2df10313343bee0523108f52c97180b64c73257868a6968859bac5326aa54d2f85ecac814102f3f3fac8b953752163a3097b8e7912c1f56359d3149d6722d59fa2dfe54a8fd0dc6752750824a1f43cf03e30343b1ef0e63d35191b05eec3655e9a60dfed78e1827d7b15f458865eacced082dbfb3355b3390d4a9ff8f25f8cb61e4844a23f102bc17d4bf5308f0264524f0cfd0a5eaa42b5fbafd9736efe8ea5907d22f293a4ddadfb9dac8d522c7a09adb6f38d0c294f54d043d053b428e5c9d73d85b966a61639a60a6a164d50fa4bc510caab2ddd75266cd41e16fdd61f96dc54d243395f1a2bdc00c532ea179fc78d29f91eaf678235b498244d2323e83227cfd3977d4b28c689a769a268240d5c9b337858bcce23d66d0aa85da16a0dc017871bcc90494489db1682f88296278b32e829d5673e0d54310eed45be4fd3e9d732843b55c6b902c57a65ff5735331be1e62005cbaa4acf27ca208c6daff6a6b6dc7e25d88ef43f469435b78ffeb243c7cfc1c0483b0e47e11586c533c5c74f662cf7a1e0d037aa7d722fb348d9fff88b2244f44b7bab8ee3cf5425fdf6bc6f6b6395cdd4dd0fda76c89aed47aca19298118ab55b1c2bcf54dfb91c4f42a66aa808d68780f49b20adef3de077b38a9701e2d2fa0f8d5c42413da26b0c0480c6824dd019924c4a1ab3edeb38d43fd8cd17fc1898c777826a20710f1b973f72532156abb8e72285516a372f1821e0f016b08a0844bd8f10e9163913e9fb570aea5499123c0b7e3549ca99b5aa2f784a6cf2e49d7bc192b40e0a8af31ed9296280c12a9131e70eb7e8aa6530879e06c87ad52e0ef1206d643be317c72bc1a4cb1cd28f5c2a57ccde6fd8591ee4b18c18af86e04b9cd3df18e0ae2af4185000000000a002d0235c46e929009dde8c80b089c3026c0938c1fa6415bff483969db1468d34f1c884457b87da48b1f17d2e39645da5a9c16af84ada92e69c5c4074e17098ff957ac1a1cf790ef1791055d45fb21c0c07c070cbc32cf0335a45c61a2034fd6c26207cf2cb43ae3dded6af8df61562548e7162f543174d6921349c7be44e484384aa181215342a64ce1c3d43a0114200a80f0e7b59ada97b9b85e6db722338d49b3f0d14c3a4a94d582aa4885f73c1d0fceecc6a8753dab512608eb26ddfe75f5944c630816d3bb7eb8364ef7e10b5c656263a4e7b9a76cafc5eebe1688c7d64a2a40621189025e45695003c2cf2571875b491cc559fea4ddbc90c4776df8deb5b5f2f9f4dba77350a94f2a907210c2127f8d0de02b8f16789b78c42af556ce9eefaddb8c7df925f349e2aa96aea00dce63d01ae8a63af555d8dae49855267c583a4fbb8b664e527f77f10c35ec97dfff65ac2b72a15a6b046a1431dc8efad25ef518d519b2a60de84e2dfe25cd501d1560beeadc87dc141104e51b7872434de589ea6481fd2e1d3495f7d23f270e7f9761054745baa18024c346f5a1b92e7eb50a5f29eb600a7dc0faa1ffc466391074defcf58a041db4ec517479196c9e8c5d81b3eb2a1397da5294579f44ba9fb091100aea646250a9d7d00543911997b1ae429ff19b0a1218fc7394f00aaa8792b78572e38581c2cc9d0a0c51a7a7b7b9dc8354004bf4c42857297d0270fa807af3e24b9621d232352a3c3514434f1de607af2385bfe0b3c54d446650e10dfb9031ad9b9d9d433e900012723382dd7f8ed3a226b96e10667ce670641010f07737f0cf79290d5c0acfc05c267bb843015bfe6e6f7f71f4f9a7eb8bdee2930a4d378988b84d6ef7717568ff54f05053a80a02f0ce32e432d7ab9b64bb36b20698e79643aa18b28585b4b1e88708c28c9acd22122eb6e5287624dfa835a5e03639f48ae71d5cf03c2630f0e9e89a10bab5691d31ffefe36e7a60dbf4886725d78eddc4f0e531fc4587824b1d3562b804f9b8254f0145f8e9d7e2545299db07c90674f889757208377bb7c9e88f8024b0c62935bdda2654fc40dfe151650311ade463e4da983e5c8ee3cf29689ef9b77a183ba90e6a84bda69cc4ec272e05dc7bb4a55807fb78171fdc3e8e47e33566e73e053272ed531bad8bae867784abb60004088d98414807a2c86df7588a7fdbf1c9b110ca490c121014f009b5d3e4d1d727b2c74a02998adc3e3a8e6eb28935efc7fda51dcc2db06d62918c4a994102d202995379bd98860fe474be43cbe54b9d9db9503eb9b72a1b31cb22e82f7810e00009c7a2543b6ba0b860217fcc8c88a2584b9cb9e0afa909cd8df1a9fae599e7b8229abf29b8472ef9373817e25b5000842ebf72319d24c8e0fa10fec6048123f3580d653109ef84b0a5f6a3a992fb39346e697dff104f3426e83e746ff8edf123f15feec66f2c20524c1d6743535e32372de313441795acdf6261e12408df31c58a5626ffec0731393f64154929e10112227bc2a1c927d58127d980461c49925a93112b8bc71a57b48775aa337e2947bd15452260bf19424728a4fd5c44c87457ce3386fa0cf47f5aa2b7d1d82352b4de6428bde3958b987f0e508d9f31e0458fd458b3500a8d9ec1fee7ad6fccb3da94823ef55f6ce8f84bb1bc8665bacc6c5390db8e6fd3cd2c54838509fc913d77c8728109a26b32c422a2ae1e0a59e234e4c2ec66cdbf21a9ee060b3a166f5dc15c50ca91dd395f0ee5a2313413ac7e52647df9a84ae91b8222d7f93fc5c5787e86c81f0c5485c44f8829cb56dff488e510a96165c0b02b227834b4ed87d8f4db079d54558a7f942757e07c70ba99821b1a827e230e38610dced3271fb94fcf510d24f626805c6a41942797bdfba427d31a17f9bf02e74143ff5c484c32b7c60feec68af406d5e6afda905bfcba439fe3d7cc704f79e8ac564fed59006f0013b7208220c2e6a85c4eef626ffebd483ce9a1d8713f68abcfedb6940b007bfead624b682bcef8168dfbd6171e067babcdb96ec8bb2f6e632a9c0fd86128a4e92b40741fb428c16474bfedde6b996f047fe281fad73d9d86a4484a31715040dd233bfe60d691595a262e6b19f8b4961149d1452300bde251b1914f8be8b7d6";

    @Before
    public void setUp() throws Exception {
        clientC0C1 = IoBuffer.allocate(1537);
        // put the handshake type in the first position
        clientC0C1.put(RTMPConnection.RTMP_NON_ENCRYPTED);
        fillBuffer(clientC0C1, "RTMP-C0C1.dat");
        clientC2 = IoBuffer.allocate(1536);
        fillBuffer(clientC2, "RTMP-C2.dat");
        serverS0S1S2part1 = IoBuffer.allocate(1537);
        // put the handshake type in the first position
        serverS0S1S2part1.put(RTMPConnection.RTMP_NON_ENCRYPTED);
        fillBuffer(serverS0S1S2part1, "RTMP-S0S1S2-01.dat");
        serverS0S1S2part2 = IoBuffer.allocate(1536);
        fillBuffer(serverS0S1S2part2, "RTMP-S0S1S2-02.dat");
    }

    @After
    public void tearDown() throws Exception {
        clientC0C1.free();
        clientC2.free();
        serverS0S1S2part1.free();
        serverS0S1S2part2.free();
    }

    private void fillBuffer(IoBuffer buf, String byteDumpFile) throws Exception {
        File f = new File(String.format("%s/target/test-classes/%s", System.getProperty("user.dir"), byteDumpFile));
        FileInputStream fis = new FileInputStream(f);
        log.info("File: {} length: {}", byteDumpFile, f.length());
        ByteBuffer bb = ByteBuffer.allocate((int) f.length());
        fis.getChannel().read(bb);
        bb.flip();
        buf.put(bb);
        buf.flip();
        log.debug("Filled buffer: {}", buf);
        fis.close();
    }

    @Test
    public void testClientDigest() throws InterruptedException {
        log.info("\ntestClientDigest");
        OutboundHandshake out = new OutboundHandshake();
        int algorithm = 0;
        byte[] handshakeBytes = out.generateClientRequest1().array();
        // get the handshake digest
        int digestPos = out.getDigestOffset(algorithm, handshakeBytes, 0);
        log.debug("Digest position offset: {}", digestPos);
        out.calculateDigest(digestPos, handshakeBytes, 0, RTMPHandshake.GENUINE_FP_KEY, 30, handshakeBytes, digestPos);
        log.debug("Calculated digest: {}", Hex.encodeHexString(Arrays.copyOfRange(handshakeBytes, digestPos, digestPos + 32)));
        Assert.assertTrue(out.verifyDigest(digestPos, handshakeBytes, RTMPHandshake.GENUINE_FP_KEY, 30));
    }

    /** Clientside test */
    @Test
    public void testOutboundHandshake() {
        log.info("\ntestOutboundHandshake");
        OutboundHandshake out = new OutboundHandshake();
        // set the handshake type
        out.setHandshakeType(RTMPConnection.RTMP_NON_ENCRYPTED);
        // called initially with null input which triggers creation of C1
        //IoBuffer C1 = hs.doHandshake(null);
        //log.debug("C1: {}", C1);
        //log.debug("C0C1 bytes: {}", new String(clientC0C1.array()));

        // create C0+C1
        IoBuffer C1 = ((OutboundHandshake) out).generateClientRequest1();
        log.debug("C1: {}", C1);
        //log.debug("C0C1 bytes: {}", new String(clientC0C1.array()));

        // strip 03 byte
        serverS0S1S2part1.get();
        // send in the first part of server handshake
        IoBuffer C2 = out.decodeServerResponse1(serverS0S1S2part1);
        Assert.assertNotNull(C2);
        log.debug("S1 (first): {}", C2);
        // send in the second part of server handshake, this creates C2
        boolean res = out.decodeServerResponse2(serverS0S1S2part2);
        Assert.assertTrue(res);
        log.debug("S2 (second): {}", res);

        //log.debug("Server bytes1: {}", new String(serverS0S1S2part1.array()));
        //log.debug("Server bytes2: {}", new String(serverS0S1S2part2.array()));

        // put parts 1 and 2 together
        IoBuffer S0S1S2 = IoBuffer.allocate(3073);
        S0S1S2.put(serverS0S1S2part1);
        S0S1S2.put(serverS0S1S2part2);
        S0S1S2.flip();
        // strip the 03 byte
        S0S1S2.get();
        // send in the combined server handshake, this creates C2
        C2 = out.decodeServerResponse1(S0S1S2);
        log.debug("C2 (third): {}", C2);
    }

    @Test
    public void testValidate() {
        log.info("\ntestValidate");
        // client side handshake handler
        OutboundHandshake out = new OutboundHandshake();
        // set the handshake type
        out.setHandshakeType(RTMPConnection.RTMP_NON_ENCRYPTED);
        // try SO12
        IoBuffer S0S1S2 = IoBuffer.allocate(3073);
        S0S1S2.put(serverS0S1S2part1);
        S0S1S2.put(serverS0S1S2part2);
        S0S1S2.flip();
        // strip the 03 type byte
        S0S1S2.get();
        log.debug("Validate server: {}", S0S1S2);
        boolean server = out.validate(S0S1S2.array());
        log.debug("Handshake is valid: {}", server);
        // XXX S0S1S2 data needs to be regenerated, what we have is corrupt
        //Assert.assertTrue(server);
    }

    @Test
    public void testValidateFromYouTube() {
        log.info("\ntestValidateFromYouTube");
        // client side handshake handler
        OutboundHandshake out = new OutboundHandshake();
        // server response
        IoBuffer y = IoBuffer.allocate(0);
        y.setAutoExpand(true);
        y.put(IOUtils.hexStringToByteArray(youtubeS1));
        y.flip();
        log.debug("Validate youtube: {}", y);
        @SuppressWarnings("unused")
        boolean youtube = out.validate(y.array());
        //boolean decoded = out.decodeServerResponse1(y);

        //Assert.assertTrue(youtube && decoded);
    }

}
