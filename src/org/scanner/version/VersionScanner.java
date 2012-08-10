package org.scanner.version;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class VersionScanner {

	private static class Artifact {
		public String group;
		public String id;

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
	private Map<Artifact, Map<String, Set<String>>> artifacts;

	public void process(String directory) {
		artifacts = new HashMap<Artifact, Map<String, Set<String>>>();
		scanDir(new File(directory), directory.length());
		for (Map.Entry<Artifact, Map<String, Set<String>>> entryArtifact : artifacts.entrySet()) {
			Map<String, Set<String>> version = entryArtifact.getValue();
			if (version.size() > 1) {
				Artifact artifact = entryArtifact.getKey();
				System.out.println("group '" + artifact.group + "' artifact '" + artifact.id + "':");
				for (Map.Entry<String, Set<String>> entryVersion : version.entrySet()) {
					System.out.println("\tversion '" + entryVersion.getKey() + '\'');
					for(String name : entryVersion.getValue()) {
						System.out.println("\t\t@ " + name);
					}
				}
			}
		}
	}

	private void scanDir(File dir, int base) {
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				scanDir(file, base);
			} else {
				if (file.getName().endsWith(".xml")) {
					parseXml(getDocument(file), file.getAbsolutePath().substring(base));
				}
			}
		}
	}

	private void parseXml(Document xml, String name) {
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
				String mvnPath = bundle.getTextContent();
				mvnPath = mvnPath.substring(mvnPath.lastIndexOf(':') + 1);

				Artifact artifact = new Artifact();
				int index = mvnPath.indexOf('/');
				artifact.group = mvnPath.substring(0, index);
				mvnPath = mvnPath.substring(index + 1);
				index = mvnPath.indexOf('/');
				artifact.id = mvnPath.substring(0, index);
				String version = mvnPath.substring(index + 1);
				Map<String, Set<String>> versions = artifacts.get(artifact);
				if (null == versions) {
					versions = new HashMap<String, Set<String>>();
					artifacts.put(artifact, versions);
				}
				Set<String> names = versions.get(version);
				if (null == names) {
					names = new HashSet<String>();
					versions.put(version, names);
				}
				names.add(name);
			}
		}
		
	}

	private static Document getDocument(File file) {
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
			return DB.parse(file);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("specify Karaf system directory to scan");
		}
		new VersionScanner().process(args[0]);
	}

}
