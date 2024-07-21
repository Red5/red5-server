package org.red5.io.obu;

import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.List;

import org.junit.Test;
import org.red5.io.rtp.AV1Packetizer;
import org.red5.io.utils.IOUtils;

public class OBUParserTest {

    @Test
    public void TestAll() throws Exception {
        // fragmented across two RTP packet payloads
        byte[] buffer = IOUtils.hexStringToByteArray(
                "680b0800000024c4ffdf00680230106a7440c71c7116c6500000002d04bb0a1254d78ddb5ef291eec6feaee51e5d2ef203eb4a8e94a1123f6af89754145bd9355aae123019031818855639dec9d6ec68665aff33826db7fd981255db7c347295453229199c9ee787ccde2cf87ca0ebffe0a2d7ab86849c7f9a87befae46e977d89998442eddbb5414d39f5948a957db5a87a18e6bd562b05d22811435c7efdc2a4a3953534c47fff31ff180b94d7e50c27518a83a17113512d264cfe5e2832f48522862f7920f5bead27d103696b67bfadafec061ed4e773c301ff1979868ea2dfcb37dff35f3b47d86ebc565fb20551730b30c637056710fe3d472c4ab1778a728a8649902c4219d4f09dad2aaab1c52730af0bf9da96304ee1f02b2d51f06c6ec0b2fd7b39b51ba4d569e59587fcfeb4315f4fbba3be251763a53a4a1a73f970a508069f5a2f07263ec0f2d6051098337d8bab7f00722c884beaaac8adfefe8ff3e34be4691076f75f69ee09e9bab907fa85b24509a2ba6e7567ece248bfa3b3ce4d2f252966ba44866a53f10c1d9f3ae816051426317301df23aa5c0469cd47b7ffd7898207b140a8a601e3bd22b2da8762de3d1776dc3f8b3401ba09de9544bd5c0eb3a5c58e1dacd82f1dbe2562a576275e775c936c9b761dfb6fb3e12e39b5f3ba5ab0ab1f73a963e8b54f80d3b2e1a0883b2acc5f40bc5f958906a9faeae4e340bca368a4f094e3f70a1ba76d0ff73d5e9a77aee70283071e88d3bfc2007beaa9d51b5e813bcc497837c49fc7a0375bdebc7354af014afb437e808b1c0686878b573da9ef4020211a1bf48a8001cbb21190d6aa2168f80601423914f1ab3bbfdc17fdec80e6ea3e2b851bab43cb84ed8f5797f4f5487d1bfca9e0c5bf86845333af055208ab92883ffa405fe9fdb6ecd24f303b3ca84f838833b258e137b699854c36810ea8a44c94af8ede1854052869d8eb178694fc3c0e792587ec6c07e3e21e6ae3ea2ad8287e0224ef3972b3a56ef7b4c8c62a586fb022535470f6008dbc549d79c81701f87ce2167a8542b6a9e5a45aa58e28193581c9c189e3db7fdc5a4615d3589db937033fcabcd5c934f3c59b90bd2c707f67053b157d5b03a6780b53e7cc63e96419b62f2fa705e294921580be726b6dc83a1d7a1b5455361ce9a99bb2ab0d53487374c1339097ddc15f5e82d07bc1628b5d91905626f0787f632a14d0c5e23cbdc58bfa9e1e15c09f25f91403fa830a4498c7aece817102421f7498b61ed6ba73735e8a083927893e67484b1fd331eedfce2af51e3ab6a1627ef6481497c7af5790063b4aa41d16d97823a3832137a36277519c796bcdeb918402369e639b4db1f2a0275b42882574077a65f3709a1a8f87b9a538f1335627b0f64a23217a36cc5866a438d49d9463de1879e5d3780f4a73e16f13e550ed726767e02eec80b3ddbfe1db81246131111f20078cb4220a463b3f6a98f6223896068913afaeddbdbf356ba99f4204ac7c6cd0be8a39ee956878d21c9333a2ce380575c497415fdb55102c02bbc4d26716a642f4f4b6165970e4ccbe024e7aa3adadbee6ee98b900316c90a6e749400f86dd50d9251da9499ab02cedc824454bbaa4c05aa802baaf53af107850cfb949193b582ab5dbe2");
        byte[] buffer2 = IOUtils.hexStringToByteArray(
                "d03dea919e0c20f6f77b8768cbbcbf64bf5cf95fdaf918c56e93d2ac1a282d962bf8b508fe76ba48fe3e9245f3072f8f7ae4a96e10d07dc23d4d1e7169ff50e7f82f3e75b4d1c769a575121fa4b2fe3c8ff52b6f4ebddcfe0b8b6d09630aa8ded076ebad13e23f90f6dd0a3d08bf30a4e3f6adfd18de784cb2e3ea40fa198f071def1c4b4175ba07d55d6896dd016a6637fa44ca5b1701a736ab4c07213194e017772329e2e8f83807e79debe528d8025bfc0674804981b862440620e1fda5e529557586ce60dd394bc4b575b296e07fa547c84f70314bf0a3451b3a7e912fbcd4488fe7f71bd0591ed7c3a216a5f3b724a249ff716507660c1b6930b160ec9746cf9ecfbf500005178c519f96ab730c8a4a430bce6e94a65b0dbab217523854a7480ce42e2b7c5ed51e28256579b1c86bda94c27d9ed606c22cb5e6240cc1354aa19cdc32fefd71aef6d5d05a5d0f0d57aae7a2475ecbedc23c02d365e04522578e30f1c42167decbfabe3e0d6025815863e92ea3ad209d1f6c693bc39025c397f186d25c438300bd1c5a146f96ee490afe501e5b92ed80af78171b2871a1ff501c5927120187ca207d0118e8173e57745da8403a0ba90787dbdb4e490607d99a1f12b80a59bed2979bbcb96fabd142f0a56c735ec2bc7604b5e03f37f306af8a1fa884152c4ed2a6be6076458b7c48e9ad8c0f965fe8a7e7afdcd28c36c3e1b42282b81b6be15b85bca04f1d641d3fe0c331317a53767c57311a6a9900a3597fd6df51416d626a995cfc955b0628b9f45b4537a4148317a5d6b9a750d0c3b4c5c81571419ea0b6155443dd6e96bca398b1eb93a8579cde860578c4bdbbab2dbd2f03df2c25767b242ee15ac2bf679af211c4c885514d937d0128c7409c3e4856c3b67b62dc66a262e9e022446ac672ea3c34dfd4afbfdbd5e46d5304cd12a6e1f7e9103aa27d0798da79398a6076c03fc1d08c9aa578cae22e2e52e6817a42fd66c039548cf49b706c13a28aa69d1209e2b57c4f976b4d5d85cd9a01efd94aedbe67199592b0fd63bab2e32da4bcd80a0ce1c0d1466098d3d73895cbaab8f4b232d3b6cebb4129a9480aefabba61280893d05e80354698b3fea8bd09322345d75f80caa7ec3cd944fff4e8cec5b9eb787acd0ea8acb976201c61ce6fec3518d5e449d8315125587ffa20a71212e56ceef00b5f7731f894af79bb605cef7ad0016f9f6ceb529f5a465bf3032a0122b04e475d077e2ba4e2b1ff9bbfc06499d150bb1c374d4714eb469b4b907e59de114445b8e3da174fc7052c11df0a9520c0b8e2cc0de2025ed6499a7c7d39e0a72d38c0f9e9bbb6417e735c0713eb6cd4396907840d88a58b681be57ada23ae10c6f0a16208ec85fcc5418052fff1a85dc0dfcfea5e2f0cacc778bb3a29c30dab4f232698ae3ab0fbdc81c14b659b2d4ede4e32fa40466350c9dc0bac22b272cf545df53f5c04033f11311f1f230f811e0107594e1da81586088a646607ed279231474c2957b4fbfeb7ec26c4cd5964046f15b31db8c73bc8753cf8888b46c9a9077f6f6a88babb84a2a351a67ba69550d8146ddbda35b9a218f57df518a2a5551d4f48d2998bc43fe6f784e368e0ab9eca0844e20cfdf5805446cd840656c484dd89e65d041734a65f");

        // non-fragmented
        byte[] bufferx = IOUtils.hexStringToByteArray(
                "680c080000002cd6d30cd50200803010c3c007fffff8b730c000008817f90ccfc67b9c0dda558282672ff007265df6c6e312ddf9717743e6baf2ce36086392acbbbd264c05529109f537b518be5c95b12c132781c2528caf27caf293d62e4632ed7187901d0b84467fd157c10dc75b41bb8a7de92cae36981339b90c664705a2df55c409abe4fb115236278886f34abbef40a7852afe9228e4cecedc4bd0aa3cd5167674e2fa34914fdc2beaae713674e12af3d353e8ecd663f66a759568cc99be17d83b875b94dcec3209184b3758b567fbdf666c169eba72c621ac026d6b17f968222e10d7dffb24697caf1164807a9d09c41ff1d73c5ac22c8ef5ffeec27ca1e4cb1c6dd8150e403685e704bb64ca6ad9218e95a083951048fa005490e98186a04a6ebe9bf0730a17bb578117afd6701fe86d32591439d81dec59e4984d44f34f7b47d9923bd95c98d5f1c98b9db165b3e187a46acc429666db5ff9e1a172b605021fa3143efe997feb42cf760919d2d299751c67da4df487e5558bed0182f6d61c5c05969679c1618774cd298327ae47873634abc47376581b4aec0e4c2fb176087faffa6d8cdee4ae5887e7a027050df5a7fb2a7533d93b6560a41327a5e51b83787ad7ec0ced8be64e8ffe6b5dbba8ee38816f0923088f07210939f0f80317242a224484e15cf34f20dcc1e7ebbc0bfb7b2066a427e201b35fb747a1884b8c47da369860d746920b7e5b4e34501267508de7c9e496efae2bc7fa362905f592bd62b7bb9066e0ad143ee7b424f304cf221486a4b8fb8356ceaab4875a9ef20bafad40e1b55c6ba7ee9fbb1a684dc3bf224dbe5852c9cc0d8804f1f8d4fbd6adcf1384d62f900c5fb4e2d829268d7c6bab91913c25399c86083954590da4a8319fa3bcc2cbf93049c3680efc2b9fce5902fad44e11490d930cae57d774dd131a157910cc99329b576d53751f6dbbe4bca9d4db06e709b06fcab3b1edc50b8d8e70b0bf8bad2f2992dd5a193dcacaed052625eeeea9dda0e378e056992fa13f075e91fbc4b3acee07a46acb42aedf09e7d0bbc6d438587db445983821c8c13c81127e3703a8ccf3f9d99d8fc1a1ccc11be3a893912c0ae81f28134407685a8f274118c931c4c171e2f0c4f41eac29492fd0c09813a6bc5e3428a730138db4ca91266cda35b5f1bf3f353b87376340597349065904e084163ae8c428d1f5119c34f45ac0f867471c9063bc06392e8aa5a0f16b41b116bdb9507872918e8c990f7d997e773685871f2e471355f807ba7b1caabf20d0fac4e1d0b3e4f4f9578d56194adc4c83c8f130c0b5df672558d80941372e0b472b864b7338f0a06b8330803e46b509c86d3e97aa704e8c7529ec8a374a81fd92f129f0e89d8cb4392d6706cd5f250230bb6b4193551e0cc96eb5d59f80f47d9d8a0d8d3b1514c9df039c78394ea0dc3a1b8cdfaaed25da60dd306409cc9453a1adfd9ee76515b8b1da9a288051889392e303df70ba1b593bb48ab60b0aa848dfcc744c718008ecc88a73f50e3dec16f632fdf36bbaa965d187e256cdde2ca41b2581b2edeae91107f517d0ca5d07b9b2a9a9ee42339321305ed258fddd730db2935877784069ba3c951c61c6c6971cef4d910a42911d1493f57841328a0a43d43e6bb0d80e04");

        AV1Packetizer av1Packetizer = new AV1Packetizer();
        try {
            // fragmented buffer
            //System.out.println("Fragmented buffer(s) total length: " + (buffer.length + buffer2.length));
            int obuCount = av1Packetizer.depacketize(buffer);
            System.out.println("Packetized buffer 1 - " + av1Packetizer + " OBUs: " + obuCount);
            obuCount = av1Packetizer.depacketize(buffer2);
            System.out.println("Packetized buffer 2 - " + av1Packetizer + " OBUs: " + obuCount);
            List<byte[]> obuElements = av1Packetizer.getOBUElements();
            //System.out.println("Depacketized OBUs: " + obuElements.size());
            assertTrue(obuElements.size() == 3);
            List<OBUInfo> obuInfos = new LinkedList<>();
            for (byte[] obu : obuElements) {
                // parse the OBU
                OBUInfo info = OBUParser.getNextObu(obu, 0, obu.length);
                System.out.println(info);
                obuInfos.add(info);
            }
            // reset
            av1Packetizer.reset();
            // packetize the obu info list not the bytes arrays without the OBU header
            List<byte[]> payloads = av1Packetizer.packetize(obuInfos, 1176);
            System.out.println("Payloads: " + payloads.size());
            assertTrue(payloads.size() == 2);

            // run non-fragmented buffer
            //System.out.println("\nNon-fragmented buffer total length: " + bufferx.length);
            obuCount = av1Packetizer.depacketize(bufferx);
            System.out.println("Packetized buffer x - " + av1Packetizer + " OBUs: " + obuCount);

            obuElements = av1Packetizer.getOBUElements();
            //System.out.println("Depacketized OBUs: " + obuElements.size());
            assertTrue(obuElements.size() == 2);
            for (byte[] obu : obuElements) {
                // parse the OBU
                OBUInfo info = OBUParser.getNextObu(obu, 0, obu.length);
                System.out.println(info);
            }
            // reset
            av1Packetizer.reset();
        } catch (Exception e) {
            System.err.println("Error handling AV1 payloads: " + e.getMessage());
        }

        /*
        // packetizing
        int mtu = 5;

        byte[] in = new byte[] { 0x01 };
        byte[][] out = new byte[][] { { 0x00, 0x01, 0x01 } };

        LinkedList<byte[]> outList1 = marshal(mtu, in);
        System.out.printf("Equal? %b%n", Arrays.areEqual(out[0], outList1.get(0)));

        byte[] in2 = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x04, 0x05 };
        byte[][] out2 = new byte[][] { { 0x40, 0x02, 0x00, 0x01 }, { (byte) 0xc0, 0x02, 0x02, 0x03 }, { (byte) 0xc0, 0x02, 0x04, 0x04 }, { (byte) 0x80, 0x01, 0x05 } };

        LinkedList<byte[]> outList2 = marshal(mtu, in2);
        System.out.printf("Marshaled entries: %d%n", outList2.size());
        System.out.printf("Equal? %b%n", Arrays.areEqual(out2[0], outList2.get(0)));
        System.out.printf("Equal? %b%n", Arrays.areEqual(out2[1], outList2.get(1)));
        System.out.printf("Equal? %b%n", Arrays.areEqual(out2[2], outList2.get(2)));
        System.out.printf("Equal? %b%n", Arrays.areEqual(out2[3], outList2.get(3)));

        //LinkedList<byte[]> outList3 = marshal(0, new byte[] { 0x0a, 0x0b, 0x0c });
        //System.out.println("Zero mtu payload: " + outList3);
        */
    }

}