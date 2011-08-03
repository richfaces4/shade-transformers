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

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jdom.Namespace;

/**
 * @author Nick Belaevski
 *
 */
final class NamespacesTracker {

    // see MessageFormat class for format of this string
    private static final String GENERATED_NS_PREFIX_FORMAT = "x{0}";

    private Set<String> usedPrefixes = new HashSet<String>();

    private Map<String, Namespace> namespaces = new HashMap<String, Namespace>();

    private int prefixGeneratorCounter = 0;

    private String maskEmptyString(String s) {
        if (s == null || s.trim().length() == 0) {
            return "";
        }

        return s;
    }

    private Namespace createNamespace(String uri, String prefix) {
        String maskedPrefix = maskEmptyString(prefix);

        while (usedPrefixes.contains(maskedPrefix)) {
            // generate next prefix using counter & format string
            maskedPrefix = MessageFormat.format(GENERATED_NS_PREFIX_FORMAT, prefixGeneratorCounter++);
        }

        return Namespace.getNamespace(maskedPrefix, uri);
    }

    public Namespace getNamespace(String nsUri, String nsPrefix) {
        String maskedUri = maskEmptyString(nsUri);

        Namespace namespace = namespaces.get(maskedUri);

        if (namespace == null) {
            namespace = createNamespace(maskedUri, nsPrefix);

            usedPrefixes.add(namespace.getPrefix());
            namespaces.put(maskedUri, namespace);
        }

        return namespace;
    }

    public Namespace getNamespace(Namespace namespace) {
        return getNamespace(namespace.getURI(), namespace.getPrefix());
    }

    public Collection<Namespace> getNamespaces() {
        return Collections.unmodifiableCollection(namespaces.values());
    }

}
