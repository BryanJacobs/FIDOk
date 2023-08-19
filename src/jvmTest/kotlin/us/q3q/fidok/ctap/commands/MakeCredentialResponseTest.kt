package us.q3q.fidok.ctap.commands

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalStdlibApi::class)
class MakeCredentialResponseTest {

    @Test
    fun decodeLongResponse() {
        val hex = "a301667061636b6564025901522c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae410000000" +
            "d8652abe9fbd84810a840d6fc442a8c2c00cea30058aaa1f8724de91cb4a191a45374f39937b9a758af97214c6593f1dd754db" +
            "12294e147848c1da8588cd339348d2baa4345b6c4d02899fffe4406f8fcaf175ea5ef190880ee80c2cf6f451c6b756db6baadb" +
            "a7e9244f608e7e467ddd0038b3b58382ad0fdcbf7868e2012359d9db2f68185e434df590a06ea12af62ae53eb1b4c5cf71a16b" +
            "173ff2068347ae38b93cff02b0f364c78e5d1d487906bf3fe3b737da6f297f135d75e5914bd5890014cfd8f985b77406ab26f8" +
            "7b4c702507fd6c89075e2a3f298539d74b318c775a5010203262001215820ffdc0fa10a962c1fec72dc7c3f11b37e7087292f6" +
            "147d3a43c6e88ecce57beb322582024591e33acd23319db7c01d48a64e773fd3bd6c1d2ca2431ed1555edd668a88503a363616" +
            "c67266373696758473045022100a58b4fdc9219797a8065f1292e0706f909affa5c3abe653f7382da9debd1e08202202ccd0bd" +
            "00050b54f59b77b9747e88b4b76383d46742f4a61199adb95f2a4a89263783563815902aa308202a63082024ca003020102021" +
            "47d67b71789897e6395a293c5f269802b105bd228300a06082a8648ce3d040302302d3111300f060355040a0c08536f6c6f4b6" +
            "57973310b3009060355040613024348310b300906035504030c0246313020170d3231303532333030353230305a180f3230373" +
            "1303531313030353230305a308183310b30090603550406130255533111300f060355040a0c08536f6c6f4b657973312230200" +
            "60355040b0c1941757468656e74696361746f72204174746573746174696f6e313d303b06035504030c34536f6c6f2032204e4" +
            "6432b5553422d412038363532414245394642443834383130413834304436464334343241384332432042313059301306072a8" +
            "648ce3d020106082a8648ce3d030107034200040ad2c93553eec0f15f4ae89ba5e821c83cab0312b465774a3a0644d32a1c148" +
            "0876b2be0496553303517cc845f95ef50f37f33a0d096a1051b0d7c52874e8c1aa381f03081ed301d0603551d0e04160414189" +
            "37714d6c8d091bb34a27817e59873d225b4b5301f0603551d23041830168014416bb64befa2190de4625ffd290496b98229b4f" +
            "830090603551d1304023000300b0603551d0f0404030204f0303206082b0601050507010104263024302206082b06010505073" +
            "0028616687474703a2f2f692e7332706b692e6e65742f66312f30270603551d1f0420301e301ca01aa0188616687474703a2f2" +
            "f632e7332706b692e6e65742f72312f3021060b2b0601040182e51c010104041204108652abe9fbd84810a840d6fc442a8c2c3" +
            "013060b2b0601040182e51c020101040403020430300a06082a8648ce3d0403020348003045022032c2e7520e4fc7615e87121" +
            "436269ecbca9e4f5146b65275b372c45063a4b3ef022100cd12fe5aba90ef6319f68aa592372b2fe2aa5da2a48ccad450edcb5" +
            "fb890d637"
        val res = CTAPCBORDecoder(hex.hexToByteArray()).decodeSerializableValue(
            MakeCredentialResponse.serializer(),
        )

        assertNull(res.epAtt)
        assertEquals(13u, res.authData.signCount)
        assertEquals(0x41, res.authData.flags)
        assertNull(res.authData.extensions)
        assertEquals(16, res.authData.attestedCredentialData?.aaguid?.size)
        assertEquals("packed", res.fmt)
        assertEquals(1, (res.attStmt["x5c"] as Array<*>).size)
    }
}
