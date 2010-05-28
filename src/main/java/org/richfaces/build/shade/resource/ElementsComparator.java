/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.richfaces.build.shade.resource;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

/**
 * @author Nick Belaevski
 * 
 */
class ElementsComparator implements Comparator<Element> {

    private String namespaceUri;
    
    private List<String> orderedElementNames;
    
    private Map<String, XPath> comparisonPaths;
    
    public ElementsComparator(String namespaceUri, List<String> orderedElementNames, Map<String, XPath> comparisonPaths) {
        super();
        this.namespaceUri = namespaceUri;
        this.orderedElementNames = orderedElementNames;
        this.comparisonPaths = comparisonPaths;
    }

    private String maskNullString(String s) {
        return s != null ? s : "";
    }
    
    public int compare(Element o1, Element o2) {
        if (!namespaceUri.equals(o1.getNamespaceURI()) || !namespaceUri.equals(o2.getNamespaceURI())) {
            return 0;
        }

        String firstName = o1.getName();
        String secondName = o2.getName();

        if (firstName.equals(secondName)) {
            XPath comparisonPath = comparisonPaths.get(firstName);

            if (comparisonPath != null) {
                try {
                    String firstEltValue = maskNullString(comparisonPath.valueOf(o1));
                    String secondEltValue = maskNullString(comparisonPath.valueOf(o2));
                    
                    return firstEltValue.compareToIgnoreCase(secondEltValue);
                } catch (JDOMException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }

            return 0;
        } else {
            int firstNameIdx = orderedElementNames.indexOf(firstName);
            int secondNameIdx = orderedElementNames.indexOf(secondName);

            if (firstNameIdx < secondNameIdx) {
                return -1;
            } else if (firstNameIdx == secondNameIdx) {
                return 0;
            } else {
                return 1;
            }
        }
    }

}
