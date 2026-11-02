package com.areonedev.autotrack;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class BadgeContrastTest {
    @Test public void editActionUsesTranslatedAccessibleName() throws Exception {
        Element action = (Element) DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/menu/menu_lead_details.xml")).getElementsByTagName("item").item(0);
        assertEquals("@string/edit_lead", action.getAttribute("android:title"));
    }
    @Test public void smallStageBadgeMeetsNormalTextContrast() throws Exception {
        NodeList views = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/layout/item_single_lead.xml")).getElementsByTagName("TextView");
        for (int i = 0; i < views.getLength(); i++) {
            Element view = (Element) views.item(i);
            if (!view.getAttribute("android:id").equals("@+id/text_lead_stage")) continue;
            double a = luminance(view.getAttribute("android:textColor"));
            double b = luminance(view.getAttribute("android:background"));
            assertTrue("The 11sp badge requires 4.5:1 contrast", (Math.max(a, b) + .05) / (Math.min(a, b) + .05) >= 4.5);
            return;
        }
        fail("Stage badge was not found");
    }
    private double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return .2126 * channel((rgb >> 16) & 255) + .7152 * channel((rgb >> 8) & 255) + .0722 * channel(rgb & 255);
    }
    private double channel(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
