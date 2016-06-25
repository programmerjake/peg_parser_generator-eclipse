package tk.programmerjake.peg_parser_generator.eclipse.editors;

import org.eclipse.jface.text.TextAttribute;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;

public class PEGSyntaxHighlightingConstants {
	public static final RGB KEYWORD = new RGB(127, 0, 85);
	public static final int KEYWORD_STYLE = SWT.BOLD;
	public final TextAttribute keywordTextAttribute;
	public static final RGB IDENTIFIER = new RGB(0, 0, 0);
	public static final int IDENTIFIER_STYLE = SWT.NORMAL;
	public final TextAttribute identifierTextAttribute;
	public static final RGB RULE_NAME = new RGB(0, 80, 50);
	public static final int RULE_NAME_STYLE = SWT.ITALIC;
	public final TextAttribute ruleNameTextAttribute;
	public static final RGB TYPE_NAME = new RGB(0, 80, 50);
	public static final int TYPE_NAME_STYLE = SWT.BOLD;
	public final TextAttribute typeNameTextAttribute;
	public static final RGB RESULT_VARIABLE = new RGB(0, 0, 192);
	public static final int RESULT_VARIABLE_STYLE = SWT.NORMAL;
	public final TextAttribute resultVariableTextAttribute;
	public static final RGB TEMPLATE_VARIABLE = new RGB(100, 70, 50);
	public static final int TEMPLATE_VARIABLE_STYLE = SWT.BOLD;
	public final TextAttribute templateVariableTextAttribute;
	public static final RGB OPERATOR = new RGB(0, 0, 0);
	public static final int OPERATOR_STYLE = SWT.NORMAL;
	public final TextAttribute operatorTextAttribute;
	public static final RGB LINE_COMMENT = new RGB(63, 127, 95);
	public static final int LINE_COMMENT_STYLE = SWT.NORMAL;
	public final TextAttribute lineCommentTextAttribute;
	public static final RGB BLOCK_COMMENT = new RGB(63, 127, 95);
	public static final int BLOCK_COMMENT_STYLE = SWT.NORMAL;
	public final TextAttribute blockCommentTextAttribute;
	public static final RGB STRING = new RGB(42, 0, 255);
	public static final int STRING_STYLE = SWT.NORMAL;
	public final TextAttribute stringTextAttribute;
	public static final RGB CHARACTER_CLASS = new RGB(42, 0, 255);
	public static final int CHARACTER_CLASS_STYLE = SWT.ITALIC;
	public final TextAttribute characterClassTextAttribute;
	public static final RGB CODE = new RGB(100, 40, 128);
	public static final int CODE_STYLE = SWT.BOLD;
	public final TextAttribute codeTextAttribute;
	public static final RGB SUBSTITUTION = new RGB(100, 70, 50);
	public static final int SUBSTITUTION_STYLE = SWT.BOLD | SWT.ITALIC;
	public final TextAttribute substitutionTextAttribute;
	public static final RGB CODE_LINE_COMMENT = new RGB(63, 127, 95);
	public static final int CODE_LINE_COMMENT_STYLE = SWT.NORMAL;
	public final TextAttribute codeLineCommentTextAttribute;
	public static final RGB CODE_BLOCK_COMMENT = new RGB(63, 127, 95);
	public static final int CODE_BLOCK_COMMENT_STYLE = SWT.NORMAL;
	public final TextAttribute codeBlockCommentTextAttribute;
	public static final RGB CODE_KEYWORD = new RGB(127, 0, 85);
	public static final int CODE_KEYWORD_STYLE = SWT.BOLD;
	public final TextAttribute codeKeywordTextAttribute;
	public static final RGB CODE_IDENTIFIER = new RGB(0, 0, 0);
	public static final int CODE_IDENTIFIER_STYLE = SWT.NORMAL;
	public final TextAttribute codeIdentifierTextAttribute;
	public static final RGB CODE_STRING = new RGB(42, 0, 255);
	public static final int CODE_STRING_STYLE = SWT.NORMAL;
	public final TextAttribute codeStringTextAttribute;
	public static final RGB CODE_CHAR = new RGB(42, 0, 255);
	public static final int CODE_CHAR_STYLE = SWT.NORMAL;
	public final TextAttribute codeCharTextAttribute;
	public static final RGB CODE_HEADER_NAME = new RGB(42, 0, 255);
	public static final int CODE_HEADER_NAME_STYLE = SWT.NORMAL;
	public final TextAttribute codeHeaderNameTextAttribute;
	public static final RGB CODE_NUMBER = new RGB(0, 0, 0);
	public static final int CODE_NUMBER_STYLE = SWT.NORMAL;
	public final TextAttribute codeNumberTextAttribute;
	public static final RGB CODE_PUNCTUATOR = new RGB(0, 0, 0);
	public static final int CODE_PUNCTUATOR_STYLE = SWT.NORMAL;
	public final TextAttribute codePunctuatorTextAttribute;
	public static final RGB CODE_UNKNOWN = new RGB(224, 0, 0);
	public static final int CODE_UNKNOWN_STYLE = SWT.BOLD;
	public final TextAttribute codeUnknownTextAttribute;

	public PEGSyntaxHighlightingConstants(ColorManager colorManager) {
		keywordTextAttribute = new TextAttribute(colorManager.getColor(KEYWORD), null, KEYWORD_STYLE);
		identifierTextAttribute = new TextAttribute(colorManager.getColor(IDENTIFIER), null, IDENTIFIER_STYLE);
		ruleNameTextAttribute = new TextAttribute(colorManager.getColor(RULE_NAME), null, RULE_NAME_STYLE);
		typeNameTextAttribute = new TextAttribute(colorManager.getColor(TYPE_NAME), null, TYPE_NAME_STYLE);
		resultVariableTextAttribute = new TextAttribute(colorManager.getColor(RESULT_VARIABLE), null,
				RESULT_VARIABLE_STYLE);
		templateVariableTextAttribute = new TextAttribute(colorManager.getColor(TEMPLATE_VARIABLE), null,
				TEMPLATE_VARIABLE_STYLE);
		operatorTextAttribute = new TextAttribute(colorManager.getColor(OPERATOR), null, OPERATOR_STYLE);
		lineCommentTextAttribute = new TextAttribute(colorManager.getColor(LINE_COMMENT), null, LINE_COMMENT_STYLE);
		blockCommentTextAttribute = new TextAttribute(colorManager.getColor(BLOCK_COMMENT), null, BLOCK_COMMENT_STYLE);
		stringTextAttribute = new TextAttribute(colorManager.getColor(STRING), null, STRING_STYLE);
		characterClassTextAttribute = new TextAttribute(colorManager.getColor(CHARACTER_CLASS), null,
				CHARACTER_CLASS_STYLE);
		codeTextAttribute = new TextAttribute(colorManager.getColor(CODE), null, CODE_STYLE);
		substitutionTextAttribute = new TextAttribute(colorManager.getColor(SUBSTITUTION), null, SUBSTITUTION_STYLE);
		codeLineCommentTextAttribute = new TextAttribute(colorManager.getColor(CODE_LINE_COMMENT), null,
				CODE_LINE_COMMENT_STYLE);
		codeBlockCommentTextAttribute = new TextAttribute(colorManager.getColor(CODE_BLOCK_COMMENT), null,
				CODE_BLOCK_COMMENT_STYLE);
		codeKeywordTextAttribute = new TextAttribute(colorManager.getColor(CODE_KEYWORD), null, CODE_KEYWORD_STYLE);
		codeIdentifierTextAttribute = new TextAttribute(colorManager.getColor(CODE_IDENTIFIER), null,
				CODE_IDENTIFIER_STYLE);
		codeStringTextAttribute = new TextAttribute(colorManager.getColor(CODE_STRING), null, CODE_STRING_STYLE);
		codeCharTextAttribute = new TextAttribute(colorManager.getColor(CODE_CHAR), null, CODE_CHAR_STYLE);
		codeHeaderNameTextAttribute = new TextAttribute(colorManager.getColor(CODE_HEADER_NAME), null,
				CODE_HEADER_NAME_STYLE);
		codeNumberTextAttribute = new TextAttribute(colorManager.getColor(CODE_NUMBER), null, CODE_NUMBER_STYLE);
		codePunctuatorTextAttribute = new TextAttribute(colorManager.getColor(CODE_PUNCTUATOR), null,
				CODE_PUNCTUATOR_STYLE);
		codeUnknownTextAttribute = new TextAttribute(colorManager.getColor(CODE_UNKNOWN), null, CODE_UNKNOWN_STYLE);
	}
}
