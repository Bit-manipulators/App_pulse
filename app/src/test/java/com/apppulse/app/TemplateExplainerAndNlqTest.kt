package com.apppulse.app

import com.apppulse.app.domain.ai.ExplanationInput
import com.apppulse.app.domain.ai.OutputValidator
import com.apppulse.app.domain.ai.TemplateExplainer
import com.apppulse.app.domain.nlq.NaturalLanguageQueryEngine
import org.junit.Assert.*
import org.junit.Test

class TemplateExplainerAndNlqTest {

    @Test
    fun testTemplateExplainerDormantApp() {
        val input = ExplanationInput(
            app = "Old Game",
            footprintMB = 1400,
            cacheMB = 100,
            daysSinceUse = 74,
            permissions = listOf("CAMERA", "LOCATION"),
            impactBand = "High",
            driver = "dormancy"
        )
        val explanation = TemplateExplainer.generateExplanation(input)

        assertTrue(explanation.contains("Old Game"))
        assertTrue(explanation.contains("74 days"))
        assertTrue(explanation.contains("1.4 GB"))
        assertTrue(OutputValidator.isValid(explanation, input))
    }

    @Test
    fun testOutputValidatorRejectsAlarmistWords() {
        val input = ExplanationInput(
            app = "Test App",
            footprintMB = 100,
            daysSinceUse = 1,
            impactBand = "Low",
            driver = "storage"
        )
        val maliciousText = "This app is malicious and spying on your contacts!"
        assertFalse("Validator must reject alarmist terms", OutputValidator.isValid(maliciousText, input))
    }

    @Test
    fun testOutputValidatorRejectsHallucinatedNumbers() {
        val input = ExplanationInput(
            app = "Test App",
            footprintMB = 100,
            daysSinceUse = 5,
            impactBand = "Low",
            driver = "storage"
        )
        val hallucinatedText = "This app drained 98 percent battery and leaked 492 records."
        assertFalse("Validator must reject numbers not in input", OutputValidator.isValid(hallucinatedText, input))
    }

    @Test
    fun testNlqQueryParser() {
        val query1 = "Which apps over 500 MB haven't I used in 60 days?"
        val filter1 = NaturalLanguageQueryEngine.parseQuery(query1)
        assertEquals(500.0, filter1.minSizeMB!!, 0.1)
        assertEquals(60, filter1.minDaysUnused)

        val query2 = "What should I fix first?"
        val filter2 = NaturalLanguageQueryEngine.parseQuery(query2)
        assertTrue(filter2.isFixFirst)

        val query3 = "Show apps with location permission"
        val filter3 = NaturalLanguageQueryEngine.parseQuery(query3)
        assertEquals("Location", filter3.permissionCategory)
    }
}
