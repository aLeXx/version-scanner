package org.scanner.version;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.ops4j.pax.url.mvn.Handler;
import org.ops4j.pax.url.mvn.internal.Parser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class VersionScanner {

    public static final boolean CHECK_EXISTING_ONLY = true;

    private static class Artifact {
        private final String group;
        private final String id;

        public Artifact(String group, String id) {
            this.group = group;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Artifact) {
                Artifact artifact = (Artifact) obj;
                return id.equals(artifact.id) && group.equals(artifact.group);
            }
            return super.equals(obj);
        }
        @Override
        public int hashCode() {
            return id.hashCode() ^ group.hashCode();
        }
    }

    private static DocumentBuilder DB;
    private final Map<Artifact, Map<String, Set<String>>> artifacts = new HashMap<Artifact, Map<String, Set<String>>>();

    public void process(File directory) throws IOException {
        Properties properties = new Properties();
        InputStream is = new FileInputStream(new File(directory, "etc/org.apache.karaf.features.cfg"));
        properties.load(is);
        is.close();

        File system = new File(directory, "system");
        System.setProperty("org.ops4j.pax.url.mvn.localRepository", system.getPath());
        System.setProperty("org.ops4j.pax.url.mvn.repositories", system.toURI().toString());
        URL.setURLStreamHandlerFactory(new URLStreamHandlerFactory() {
            @Override
            public URLStreamHandler createURLStreamHandler(String protocol) {
                if ("mvn".equals(protocol)) {
                    return new Handler();
                }
                return null;
            }
        });

        String featuresRepositories = (String) properties.get("featuresRepositories");
        for(String featuresRepository : featuresRepositories.split(",")) {
            URL url = new URL(featuresRepository);
            is = url.openStream();

            parseXml(getDocument(is), featuresRepository);

            is.close();
        }

        for (Map.Entry<Artifact, Map<String, Set<String>>> entryArtifact : artifacts.entrySet()) {
            Map<String, Set<String>> version = entryArtifact.getValue();
            if (version.size() > 1) {
                Artifact artifact = entryArtifact.getKey();
                System.out.println("[" + artifact.group + "] " + artifact.id);
                for (Map.Entry<String, Set<String>> entryVersion : version.entrySet()) {
                    System.out.println('\t' + entryVersion.getKey());
                    for(String name : entryVersion.getValue()) {
                        System.out.println("\t\t" + name);
                    }
                }
            }
        }
    }

    private void parseXml(Document xml, String name) throws IOException {
        Element features = xml.getDocumentElement();
        if(!"features".equals(features.getNodeName())) {
            return;
        }
        for (Node nodeFeature = features.getFirstChild(); nodeFeature != null; nodeFeature = nodeFeature.getNextSibling()) {
            if (nodeFeature.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element feature = (Element)  nodeFeature;
            for (Node nodeBundle = feature.getFirstChild(); nodeBundle != null; nodeBundle = nodeBundle.getNextSibling()) {
                if (nodeBundle.getNodeType() != Node.ELEMENT_NODE
                        || !"bundle".equals(nodeBundle.getNodeName())) {
                    continue;
                }
                Element bundle = (Element) nodeBundle;
                try {
                    URL bundleURL = new URL(bundle.getTextContent());
                    if (CHECK_EXISTING_ONLY) {
                        bundleURL.openStream().close();
                    }

                    Parser mvnPath = new Parser(bundleURL.getPath());

                    Artifact artifact = new Artifact(mvnPath.getGroup(), mvnPath.getArtifact());
                    Map<String, Set<String>> versions = artifacts.get(artifact);
                    if (null == versions) {
                        versions = new HashMap<String, Set<String>>();
                        artifacts.put(artifact, versions);
                    }
                    String version = mvnPath.getVersion();
                    Set<String> names = versions.get(version);
                    if (null == names) {
                        names = new HashSet<String>();
                        versions.put(version, names);
                    }
                    names.add(name);
                } catch (Exception e) { // pax-url-mvn throws RuntimeException instead of IOException
                    // no such artifact in local repo
                }
            }
        }
    }

    private static Document getDocument(InputStream is) {
        try {
            if (null == DB) {
                //get the factory
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setValidating(false);
                dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    
                //Using factory get an instance of document builder
                DB = dbf.newDocumentBuilder();
            }
    
            //parse using builder to get DOM representation of the XML file
            return DB.parse(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("specify Karaf directory to scan");
        }
        new VersionScanner().process(new File(args[0]));
    }

}
