package tk.programmerjake.peg_parser_generator.eclipse.editors;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.presentation.IPresentationDamager;
import org.eclipse.jface.text.presentation.IPresentationRepairer;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;

final class PEGDamagerRepairer implements IPresentationDamager, IPresentationRepairer {

	private final ITokenScanner tokenScanner;
	private final TextAttribute defaultTextAttribute = new TextAttribute(null);
	private IDocument document;

	public PEGDamagerRepairer(PEGParser parser) {
		tokenScanner = parser.getTokenScanner();
		Assert.isNotNull(tokenScanner);
	}

	@Override
	public void createPresentation(TextPresentation presentation, ITypedRegion region) {
		int lastStart = region.getOffset();
		int length = 0;
		boolean firstToken = true;
		IToken lastToken = Token.UNDEFINED;
		TextAttribute lastAttribute = getTokenTextAttribute(lastToken);

		tokenScanner.setRange(document, lastStart, region.getLength());

		while (true) {
			IToken token = tokenScanner.nextToken();
			if (token.isEOF())
				break;

			TextAttribute attribute = getTokenTextAttribute(token);
			if (lastAttribute != null && lastAttribute.equals(attribute)) {
				length += tokenScanner.getTokenLength();
				firstToken = false;
			} else {
				if (!firstToken)
					addRange(presentation, lastStart, length, lastAttribute);
				firstToken = false;
				lastToken = token;
				lastAttribute = attribute;
				lastStart = tokenScanner.getTokenOffset();
				length = tokenScanner.getTokenLength();
			}
		}

		addRange(presentation, lastStart, length, lastAttribute);
	}

	private TextAttribute getTokenTextAttribute(IToken token) {
		Object data = token.getData();
		if (data instanceof TextAttribute)
			return (TextAttribute) data;
		return defaultTextAttribute;
	}

	@Override
	public IRegion getDamageRegion(ITypedRegion partition, DocumentEvent e, boolean documentPartitioningChanged) {

		return partition;
	}

	@Override
	public void setDocument(IDocument document) {
		this.document = document;
	}

	private void addRange(TextPresentation presentation, int offset, int length, TextAttribute attr) {
		if (attr != null) {
			int style = attr.getStyle();
			int fontStyle = style & (SWT.ITALIC | SWT.BOLD | SWT.NORMAL);
			StyleRange styleRange = new StyleRange(offset, length, attr.getForeground(), attr.getBackground(),
					fontStyle);
			styleRange.strikeout = (style & TextAttribute.STRIKETHROUGH) != 0;
			styleRange.underline = (style & TextAttribute.UNDERLINE) != 0;
			styleRange.font = attr.getFont();
			presentation.addStyleRange(styleRange);
		}
	}
}
