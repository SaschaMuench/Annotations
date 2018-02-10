package de.sonnmatt.processor;

import javax.lang.model.element.Element;

/**
 * @author MuenSasc
 */
public class ProcessingException extends Exception {
	private static final long serialVersionUID = 8575964435469088700L;

	Element element;

	public ProcessingException(Element element, String msg, Object... args) {
		super(String.format(msg, args));
		this.element = element;
	}

	public Element getElement() {
		return element;
	}
}
