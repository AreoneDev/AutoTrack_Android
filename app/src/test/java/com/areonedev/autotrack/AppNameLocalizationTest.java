package com.areonedev.autotrack;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.Assert.*;

public class AppNameLocalizationTest {
    @Test public void brandNameRemainsTheSameAcrossLocales() throws Exception {
        NodeList strings=DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/res/values/strings.xml")).getElementsByTagName("string");
        for(int i=0;i<strings.getLength();i++) {
            Element item=(Element)strings.item(i);
            if("app_name".equals(item.getAttribute("name"))) {
                assertEquals("AutoTrack",item.getTextContent());
                assertEquals("false",item.getAttribute("translatable"));
                return;
            }
        }
        fail("Application name is missing");
    }
}
