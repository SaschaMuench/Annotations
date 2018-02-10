package de.sonnmatt.processor;

import com.google.auto.service.AutoService;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.SourceVersion;
import javax.tools.JavaFileObject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.tools.Diagnostic;

@AutoService(Processor.class)
public class GenerateTranslationProcessor extends AbstractProcessor {

	public static final String	PATH_SEPARATOR	= "\\";

	private Elements						elementUtils;
	private Filer							filer;
	private Messager						messager;
	private Map<String, String>				options;
	private HashMap<String, AttributeSet>	namedAttribMap;

	private class AttributeSet {
		public String	field;
		public String	type;
		public String	dataType;
		public String	dataTypeShort;
		public String	internal;
		public String	sections;
		public String	section;
		public String   returnValue;
	}

	private class PrintWriterEnhanced {
		private PrintWriter printWriter;

		public PrintWriterEnhanced(PrintWriter printWriter) {
			this.printWriter = printWriter;
		}

		public void println() {
			printWriter.println();
		}

		public void println(String line) {
			printWriter.println(line);
		}

		@SuppressWarnings("all")
		public void println(String line, String... args) {
			println(String.format(line, args));
		}

		public void println(int tabs, String line) {
			String tabStr = "";
			for (int i = 0; i < tabs; i = i + 1) {
				tabStr = tabStr + "\t";
			}
			println(tabStr + line);
		}

		@SuppressWarnings("all")
		public void println(int tabs, String line, String... args) {
			println(tabs, String.format(line, args));
		}

	}

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		elementUtils = processingEnv.getElementUtils();
		filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();
		options = processingEnv.getOptions();
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		Set<String> annotataions = new LinkedHashSet<String>();
		annotataions.add(GenerateTranslation.class.getCanonicalName());
		return annotataions;
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	public GenerateTranslationProcessor() {
		super();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			printInfo("processing started");
			for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(GenerateTranslation.class)) {
				// Check if a class has been annotated with @GenerateTranslation
				if (annotatedElement.getKind() != ElementKind.CLASS) {
					throw new ProcessingException(annotatedElement, "Only classes can be annotated with @%s",
							GenerateTranslation.class.getSimpleName());
				}
				printInfo("Processing: " + annotatedElement.getSimpleName());

				generateCode(annotatedElement);
			}
		} catch (ProcessingException e) {
			printInfo("an error occured: %s", e.getMessage());
			printError(e.getElement(), e.getMessage());
		}
		return true;
	}

	private void generateCode(Element annotatedElement) {
		try {
			printInfo("generateCode");
			GenerateTranslation annotatedXML = annotatedElement.getAnnotation(GenerateTranslation.class);
			printInfo("xmlFileName: %s", annotatedXML.value());
			TypeElement typeElement = (TypeElement) annotatedElement;

			TypeElement superClassName = elementUtils.getTypeElement(typeElement.getQualifiedName());
			PackageElement pkg = elementUtils.getPackageOf(superClassName);
			
			String fullXmlFilename = typeElement.getQualifiedName() + " ";
			fullXmlFilename = fullXmlFilename.replace(typeElement.getSimpleName() + " ", "").replace(".", PATH_SEPARATOR);
			fullXmlFilename = options.get("Sourcepath") + fullXmlFilename + annotatedXML.value();
			
			File fXmlFile = new File(fullXmlFilename);
			printInfo("Loaded XmlFile: %s", fXmlFile.getAbsolutePath());
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			dbFactory.setValidating(false);
			dbFactory.setNamespaceAware(false);
			//dbFactory.setFeature("http://dl.google.com/gwt/DTD/xhtml.ent", false);
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);
			doc.getDocumentElement().normalize();
			
			namedAttribMap = getDefNodes(doc.getChildNodes());
			
			for (HashMap.Entry<String, AttributeSet> e : namedAttribMap.entrySet()) {
				printInfo("Looping key: %s", e.getKey());
				
				JavaFileObject f = filer.createSourceFile(e.getValue().type);
				Writer w = f.openWriter();
				PrintWriter pwOrg = new PrintWriter(w);
				PrintWriterEnhanced pw = new PrintWriterEnhanced(pwOrg);
				String constructor = "";
				try {
					pw.println("/**");
					pw.println(" * This class is generated by an annotation based on the file %s", annotatedXML.value());
					pw.println(" * ");
					pw.println(" * Do not change! File will be generated automatic. All changes will be lost");
					pw.println(" */");
					pw.println("package %s;", pkg.getQualifiedName().toString()); // package
					pw.println();
					for (HashMap.Entry<String, AttributeSet> entry : namedAttribMap.entrySet()) {
						if (e.getKey().equals(entry.getKey())) {
							pw.println("import %s;", entry.getValue().dataType);
							pw.println("import %s;", entry.getValue().sections);
						}
					}
					pw.println();
					pw.println("/** Do not change! File will be generated during build phase. **/");
					pw.println();
					pw.println("public class %s {", shortName(e.getValue().type)); 
					// generate private internal variables for DTO
					for (HashMap.Entry<String, AttributeSet> entry : namedAttribMap.entrySet()) {
						if (e.getKey().equals(entry.getKey())) {
							pw.println(1, "private %s %s;", entry.getValue().dataTypeShort, entry.getValue().internal);
							constructor = constructor + entry.getValue().dataTypeShort + " " + entry.getValue().internal + ", ";
						}
					}

					// generate constructor
					pw.println();
					pw.println(1, "public %s(%s) {", shortName(e.getValue().type), constructor.substring(0, constructor.length() - 2));
					for (HashMap.Entry<String, AttributeSet> entry : namedAttribMap.entrySet()) {
						if (e.getKey().equals(entry.getKey())) pw.println(2, "this.%1$s = %1$s;", entry.getValue().internal);
					}
					pw.println(1, "}");
					pw.println();

					// generate getter and setter for data DTO
					for (HashMap.Entry<String, AttributeSet> entry : namedAttribMap.entrySet()) {
						if (e.getKey().equals(entry.getKey())) {
							pw.println(1, "public %1$s get%2$s() {", entry.getValue().dataTypeShort, entry.getValue().dataTypeShort);
							pw.println(2, "return this.%s;", entry.getValue().internal);
							pw.println(1, "}");
							pw.println();
							pw.println(1, "public void set%1$s(%2$s %3$s) {", entry.getValue().dataTypeShort, entry.getValue().dataTypeShort, entry.getValue().internal);
							pw.println(2, "this.%1$s = %1$s;", entry.getValue().internal);
							pw.println(1, "}");
							pw.println();
						}
					}

					if (doc.hasChildNodes()) {
						// generate functions for fields
						processNode(pw, e.getKey(), doc.getChildNodes());
					}
					pw.println("}");
					pwOrg.flush();
				} finally {
					w.close();
				}				
			} // for (HashMap.Entry<String, AttributeSet> e : namedAttribMap.entrySet())
		} catch (IOException e) {
			printError(null, "%s not found.", e.toString());
		} catch (ParserConfigurationException e) {
			printInfo("Parser error: %s", e.toString());
			e.printStackTrace();
		} catch (SAXException e) {
			printInfo("SAX error: %s", e.toString());
			e.printStackTrace();
		}
	}

	private String shortName(String nodeValue) {
		return nodeValue.substring(nodeValue.lastIndexOf(".") + 1);
	}

	/**
	 * @param nodeList
	 * @return HashMap with field as key and attributeSet as value
	 */
	private HashMap<String, AttributeSet> getDefNodes(NodeList nodeList) {
		HashMap<String, AttributeSet> namedFieldMap = new HashMap<String, AttributeSet>();
		for (int count = 0; count < nodeList.getLength(); count++) {
			Node tempNode = nodeList.item(count);
			if (tempNode.getNodeType() == Node.ELEMENT_NODE) {
				if (tempNode.hasChildNodes()) {
					namedFieldMap.putAll(getDefNodes(tempNode.getChildNodes()));
				}
				if (tempNode.getNodeName().equalsIgnoreCase("ui:with")) {
					AttributeSet attribs = new AttributeSet();
					attribs.field = tempNode.getAttributes().getNamedItem("field").getNodeValue();
					attribs.type = tempNode.getAttributes().getNamedItem("type").getNodeValue();
					NodeList tempChildNodes = tempNode.getChildNodes();
					for (int i = 0; i < tempChildNodes.getLength(); i = i + 1) {
						if (tempChildNodes.item(i).getNodeName().equalsIgnoreCase("ui:attributes")) {
							NamedNodeMap attribNodes = tempNode.getChildNodes().item(1).getAttributes();
							attribs.dataType = attribNodes.getNamedItem("data").getNodeValue();
							attribs.dataTypeShort = shortName(attribs.dataType);
							attribs.returnValue  = attribNodes.getNamedItem("returnValue").getNodeValue();
							attribs.internal = attribNodes.getNamedItem("internal").getNodeValue();
							attribs.sections = attribNodes.getNamedItem("sections").getNodeValue();
							attribs.section = attribNodes.getNamedItem("section").getNodeValue();
						}
					}
					printInfo("Attributes from: %s", attribs.field);
					namedFieldMap.put(attribs.field, attribs);
				}
			}
		}
		return namedFieldMap;
	}

	private void processNode(PrintWriterEnhanced pw, String key, NodeList nodeList) {
		for (int count = 0; count < nodeList.getLength(); count++) {
			Node tempNode = nodeList.item(count);
			if (tempNode.getNodeType() == Node.ELEMENT_NODE) {
				if (tempNode.hasAttributes()) {
					NamedNodeMap nodeMap = tempNode.getAttributes();
					for (int i = 0; i < nodeMap.getLength(); i++) {
						Node node = nodeMap.item(i);
						if (node.getNodeValue().contains("{" + key + ".")) {
							generateMethod(pw, node.getNodeValue().replace("{", "").replace("}", ""));
						}
					}
				}
				if (tempNode.hasChildNodes()) {
					// loop again if has child nodes
					processNode(pw, key, tempNode.getChildNodes());
				}
			}
		}
	}

	private void generateMethod(PrintWriterEnhanced pw, String methodName) {
		printInfo("Generate method: %s", methodName);
		String metName = methodName.substring(methodName.indexOf(".") + 1);
		String prefixName = methodName.substring(0, methodName.indexOf("."));
		String section = shortName(namedAttribMap.get(prefixName).sections) + "." + namedAttribMap.get(prefixName).section;

		pw.println(1, "public %s %s() {", namedAttribMap.get(prefixName).returnValue, metName);
		pw.println(2, "return %s.get(%s, \"%s\");", namedAttribMap.get(prefixName).internal, section, metName);
		pw.println(1, "}");
		pw.println();
	}

	private void printError(Element elem, String message, Object... args) {
		printError(elem, String.format(message, args));
	}

	private void printError(Element elem, String message) {
		messager.printMessage(Diagnostic.Kind.ERROR, String.format("%s: %s", this.getClass().getSimpleName(), message), elem);

	}

	private void printInfo(String message, Object... args) {
		printInfo(String.format(message, args));
	}

	private void printInfo(String message) {
		messager.printMessage(Diagnostic.Kind.NOTE, String.format("%s: %s", this.getClass().getSimpleName(), message));
	}
}
