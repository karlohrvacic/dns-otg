package com.controldmanager.app.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficTaxonomyTest {

    /** Shapes as Dragonfly's /category-groupings and /categories send them (trimmed). */
    private val tax = parseTrafficTaxonomy(
        JSONObject("""{"body":{"groupings":[
            {"id":"tech","name":"Tech, Tools & Network","description":"cloud, hosting, VPNs, DNS","categoryIds":[1003,1018]},
            {"id":"media","name":"Arts, Entertainment & Media","categoryIds":[1072]}]}}"""),
        JSONObject("""{"body":{"categories":{
            "0":{"name":"Unclassified","sensitive":false,"dangerous":false},
            "1003":{"name":"Computers and Internet"},"1018":{"name":"Infrastructure and CDNs"},"1072":{"name":"Streaming Video"}}}}"""),
    )

    @Test
    fun `groups keep their category ids as strings, like the counts' values`() {
        assertEquals(listOf("tech", "media"), tax.groups.map { it.id })
        assertEquals(listOf("1003", "1018"), tax.groups[0].categoryIds)
        assertEquals("Infrastructure and CDNs", tax.names["1018"])
    }

    @Test
    fun `a group refinement stands for all its categories, anything else for one`() {
        assertEquals(listOf("1003", "1018"), tax.idsFor("group:tech"))
        assertEquals(listOf("1072"), tax.idsFor("1072"))
        assertNull(tax.idsFor("group:nope"))
    }

    @Test
    fun `labels and groups`() {
        assertEquals("Tech, Tools & Network", tax.label("group:tech"))
        assertEquals("Streaming Video", tax.label("1072"))
        assertEquals("media", tax.groupOf("1072")?.id)
        assertNull(tax.groupOf("0"))
    }
}
