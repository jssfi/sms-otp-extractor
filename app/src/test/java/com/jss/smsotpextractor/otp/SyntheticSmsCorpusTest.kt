package com.jss.smsotpextractor.otp

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SyntheticSmsCorpusTest {
    @Test
    fun publicTemplateInspiredOtpMessagesAreDetectedByHeuristics() = runBlocking {
        val cases = listOf(
            OtpCase("Acme: your verification code is 482913", "482913"),
            OtpCase("Acme verification code: 715204. Expires in 10 mins.", "715204"),
            OtpCase("Your sign-in code is 394821. Do not share it.", "394821"),
            OtpCase("Use 640118 to finish signing in to Acme.", "640118"),
            OtpCase("Acme 2-step verification code: 557700", "557700"),
            OtpCase("Acme password reset verification code: 801244. Expires in 10 mins.", "801244"),
            OtpCase("Acme account linking verification code: 223344", "223344"),
            OtpCase("Acme adding 2-step verification code: 908172", "908172"),
            OtpCase("Your OTP for online purchase of EUR 14.20 at StoreX is 662211", "662211"),
            OtpCase("662211 is your OTP for online purchase of EUR 14.20 at StoreX thru card 1234", "662211"),
            OtpCase("For transaction of USD 42.00 at CafeY on card 9876, the OTP is 771188", "771188"),
            OtpCase("BankApp: OTP 443322 validates your card transaction.", "443322"),
            OtpCase("Your one-time PIN is 9090. Valid for 5 minutes.", "9090"),
            OtpCase("PIN: 1842. Use it to confirm this login.", "1842"),
            OtpCase("Your security code is 392810", "392810"),
            OtpCase("Security code 845120 expires in 5 minutes.", "845120"),
            OtpCase("Login code=770055. Never share this code.", "770055"),
            OtpCase("Passcode 531800 is for your Acme login.", "531800"),
            OtpCase("Your code 123456 is for Acme.", "123456"),
            OtpCase("123456 is your Acme code.", "123456"),
            OtpCase("Acme: code 444999. It expires in 3 min.", "444999"),
            OtpCase("Acme: 231890 is your login code", "231890"),
            OtpCase("Your verification code is 883311. Ref 20260605", "883311"),
            OtpCase("Your code is 720044. Continue at https://example.test/login?id=20260605", "720044"),
            OtpCase("Your login code is 112233. https://example.test/a?session=999888777", "112233"),
            OtpCase("Steam: To disable or move your Mobile Authenticator use code: 97729", "97729"),
            OtpCase("The code to disable or move your Steam Authenticator is: 98446", "98446"),
            OtpCase("Confirm your phone number with the code 813168. Do not share this code.", "813168"),
            OtpCase("Twitch verification code: 902015", "902015"),
            OtpCase("Wise code 813168 confirms your phone number.", "813168"),
            OtpCase("Trade Republic verification code is 4800.", "4800"),
            OtpCase("Your Whop verification code is: 750740", "750740"),
            OtpCase("FedEx verification code is 334455.", "334455"),
            OtpCase("Koodi on 482913 kirjautumista varten.", "482913"),
            OtpCase("Vahvistuskoodi: 3183. Voimassa 5 minuuttia.", "3183"),
            OtpCase("Vahvista kirjautuminen koodilla 774411.", "774411"),
            OtpCase("Sinun koodi on 665544.", "665544"),
            OtpCase("Din verifieringskod ar 120045.", "120045"),
            OtpCase("Verifieringskod: 731902. Dela den inte.", "731902"),
            OtpCase("Din kod for inloggning ar 441122.", "441122"),
            OtpCase("Tu codigo de verificacion es 770088.", "770088"),
            OtpCase("Codigo de inicio de sesion: 665500", "665500"),
            OtpCase("Usa 191919 como codigo para iniciar sesion.", "191919"),
            OtpCase("Il codice di verifica e 928374.", "928374"),
            OtpCase("Codice per accesso: 118822", "118822"),
            OtpCase("Dein TAN ist 928374.", "928374"),
            OtpCase("Dein Login-Code ist 283746.", "283746"),
            OtpCase("Kennwort zum Anmelden: 334477", "334477"),
            OtpCase("Votre code de verification est : 123456", "123456"),
            OtpCase("Votre code MonApp : 456789. Valide 5 minutes.", "456789"),
            OtpCase("Code de connexion: 640220", "640220"),
            OtpCase("OTP: A1B2C3. Use it to sign in.", "A1B2C3"),
            OtpCase("Your verification code is AB12CD", "AB12CD"),
            OtpCase("Login code: X7K9Q2. Do not share.", "X7K9Q2"),
            OtpCase("Your one-time passcode is 9Z8Y7X", "9Z8Y7X"),
            OtpCase("Use code 445566 to verify. Code 445566 expires soon.", "445566"),
            OtpCase("445566 is your code. 445566 expires in 5 min.", "445566"),
        )

        cases.forEach { case ->
            val ai = FakeAiSelector()
            val detected = assertIs<OtpDecision.OtpDetected>(
                OtpProcessor(ai).process(case.sms),
                "Expected OTP for: ${case.sms}",
            )
            assertEquals(case.expectedCode, detected.code, "Wrong OTP for: ${case.sms}")
            assertEquals("heuristic", detected.source, "Expected heuristic source for: ${case.sms}")
            assertFalse(ai.called, "AI should not be needed for: ${case.sms}")
        }
    }

    @Test
    fun publicTemplateInspiredNonOtpMessagesAreRejectedBeforeAi() = runBlocking {
        val cases = listOf(
            "Your balance is 123456 EUR.",
            "Receipt 482913: paid 49.99 EUR. Balance 120000.",
            "Your order 482913 has shipped. Tracking 999888777 will update soon.",
            "Delivery update: tracking 999888777 arrives tomorrow.",
            "Invoice 482913 is due on 20260605.",
            "Statement code 482913 appears on your monthly bill.",
            "Appointment confirmed for 1200 on 20260605.",
            "Your appointment is at 1415. Ref 20260605.",
            "Sale today: use offer 123456 at checkout.",
            "Marketing opt-out id 482913.",
            "Support ticket 482913 has been updated.",
            "Case 482913 is now closed.",
            "Receipt reference 20260605 was added to your file.",
            "Your shipment id is 482913.",
            "Card ending 1234 was charged 49.99 EUR.",
            "Your account balance is 120000 points.",
            "Use coupon 554433 for 20% off.",
            "View your receipt at https://example.test/receipt?code=482913",
            "Open https://example.test/reset?token=482913 to view your request.",
            "Order 482913: visit https://shop.test/order/482913",
            "Payment of 100000,00 EUR to account DE0000000000000 is pending.",
            "Paid 482913 cents to merchant StoreX.",
            "Your package 482913 is out for delivery.",
            "Reminder: meeting room 482913 starts at 1500.",
            "Reference id=482913 status changed.",
        )

        cases.forEach { sms ->
            val ai = FakeAiSelector()
            assertIs<OtpDecision.NoOtp>(
                OtpProcessor(ai).process(sms),
                "Expected no OTP for: $sms",
            )
            assertFalse(ai.called, "AI should not be needed for: $sms")
        }
    }

    private data class OtpCase(
        val sms: String,
        val expectedCode: String,
    )

    private class FakeAiSelector : AiOtpSelector {
        var called = false

        override suspend fun select(sms: String, candidates: List<OtpCandidate>): AiOtpResult {
            called = true
            return AiOtpResult(false, null, 0.0, """{"is_2fa":false,"candidate_index":null,"confidence":0}""")
        }
    }
}
