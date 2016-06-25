package tk.programmerjake.peg_parser_generator.eclipse.editors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;

final class PEGTokenScanner implements ITokenScanner {

	private static final int EOF = -1;

	private enum IncludeState {
		StartOfLine, GotPound, GotInclude, Other,
	}

	private int tokenLength;
	private int tokenOffset;
	private int inputBegin;
	private int inputEnd;
	private int inputOffset;
	private int codeNestDepth;
	private IncludeState includeState = IncludeState.StartOfLine;
	private IDocument inputDocument;
	private int cachedPeekChar = EOF;

	private final IToken keywordToken;
	private final IToken ruleNameToken;
	@SuppressWarnings("unused")
	private final IToken resultVariableToken;
	@SuppressWarnings("unused")
	private final IToken templateVariableToken;
	private final IToken operatorToken;
	private final IToken lineCommentToken;
	private final IToken blockCommentToken;
	private final IToken stringToken;
	private final IToken characterClassToken;
	private final IToken codeToken;
	private final IToken codeLineCommentToken;
	private final IToken codeBlockCommentToken;
	private final IToken codeKeywordToken;
	private final IToken codeIdentifierToken;
	private final IToken codeStringToken;
	private final IToken codeCharToken;
	private final IToken codeHeaderNameToken;
	private final IToken codeNumberToken;
	private final IToken codePunctuatorToken;
	private final IToken codeUnknownToken;
	private final IToken substitutionToken;

	private int peek() {
		if (cachedPeekChar == EOF) {
			if (inputOffset < inputEnd) {
				try {
					char firstChar = inputDocument.getChar(inputOffset);
					if (Character.isHighSurrogate(firstChar)) {
						try {
							char secondChar = inputDocument.getChar(inputOffset + 1);
							if (Character.isLowSurrogate(secondChar)) {
								cachedPeekChar = Character.toCodePoint(firstChar, secondChar);
								return cachedPeekChar;
							}
						} catch (BadLocationException e) {
						}
					}
					cachedPeekChar = firstChar;
					return cachedPeekChar;
				} catch (BadLocationException e) {
				}
			}
			cachedPeekChar = EOF;
		}
		return cachedPeekChar;
	}

	private int next() {
		int character = peek();
		cachedPeekChar = EOF;
		int retval = 0;
		if (character != EOF) {
			retval = Character.charCount(character);
		}
		inputOffset += retval;
		return retval;
	}

	private void backup(int ch) {
		cachedPeekChar = EOF;
		Assert.isLegal(ch != EOF);
		inputOffset -= Character.charCount(ch);
		Assert.isLegal(inputOffset >= 0);
	}

	public PEGTokenScanner(ColorManager manager) {
		keywordToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.KEYWORD), null,
				IPEGSyntaxHighlightingConstants.KEYWORD_STYLE));
		ruleNameToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.RULE_NAME), null,
				IPEGSyntaxHighlightingConstants.RULE_NAME_STYLE));
		resultVariableToken = new Token(
				new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.RESULT_VARIABLE), null,
						IPEGSyntaxHighlightingConstants.RESULT_VARIABLE_STYLE));
		templateVariableToken = new Token(
				new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.TEMPLATE_VARIABLE), null,
						IPEGSyntaxHighlightingConstants.TEMPLATE_VARIABLE_STYLE));
		operatorToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.OPERATOR), null,
				IPEGSyntaxHighlightingConstants.OPERATOR_STYLE));
		lineCommentToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.LINE_COMMENT),
				null, IPEGSyntaxHighlightingConstants.LINE_COMMENT_STYLE));
		blockCommentToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.BLOCK_COMMENT),
				null, IPEGSyntaxHighlightingConstants.BLOCK_COMMENT_STYLE));
		stringToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.STRING), null,
				IPEGSyntaxHighlightingConstants.STRING_STYLE));
		characterClassToken = new Token(
				new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CHARACTER_CLASS), null,
						IPEGSyntaxHighlightingConstants.CHARACTER_CLASS_STYLE));
		codeToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE), null,
				IPEGSyntaxHighlightingConstants.CODE_STYLE));
		substitutionToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.SUBSTITUTION),
				null, IPEGSyntaxHighlightingConstants.SUBSTITUTION_STYLE));
		codeLineCommentToken = new Token(
				new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_LINE_COMMENT), null,
						IPEGSyntaxHighlightingConstants.CODE_LINE_COMMENT_STYLE));
		codeBlockCommentToken = new Token(
				new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_BLOCK_COMMENT), null,
						IPEGSyntaxHighlightingConstants.CODE_BLOCK_COMMENT_STYLE));
		codeKeywordToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_KEYWORD),
				null, IPEGSyntaxHighlightingConstants.CODE_KEYWORD_STYLE));
		codeIdentifierToken = new Token(
				new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_IDENTIFIER), null,
						IPEGSyntaxHighlightingConstants.CODE_IDENTIFIER_STYLE));
		codeStringToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_STRING),
				null, IPEGSyntaxHighlightingConstants.CODE_STRING_STYLE));
		codeCharToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_CHAR), null,
				IPEGSyntaxHighlightingConstants.CODE_CHAR_STYLE));
		codeHeaderNameToken = new Token(
				new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_HEADER_NAME), null,
						IPEGSyntaxHighlightingConstants.CODE_HEADER_NAME_STYLE));
		codeNumberToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_NUMBER),
				null, IPEGSyntaxHighlightingConstants.CODE_NUMBER_STYLE));
		codePunctuatorToken = new Token(
				new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_PUNCTUATOR), null,
						IPEGSyntaxHighlightingConstants.CODE_PUNCTUATOR_STYLE));
		codeUnknownToken = new Token(new TextAttribute(manager.getColor(IPEGSyntaxHighlightingConstants.CODE_UNKNOWN),
				null, IPEGSyntaxHighlightingConstants.CODE_UNKNOWN_STYLE));
	}

	@Override
	public int getTokenLength() {
		return tokenLength;
	}

	@Override
	public int getTokenOffset() {
		return tokenOffset;
	}

	private static boolean isWhitespace(int ch) {
		switch (ch) {
		case ' ':
		case '\t':
		case '\r':
		case '\n':
			return true;
		default:
			return false;
		}
	}

	private static boolean isCodeWhitespace(int ch) {
		switch (ch) {
		case ' ':
		case '\t':
		case '\f':
		case '\u000B':
		case '\r':
		case '\n':
			return true;
		default:
			return false;
		}
	}

	private static boolean isIdentifierStart(int ch) {
		if (ch >= 'a' && ch <= 'z')
			return true;
		if (ch >= 'A' && ch <= 'Z')
			return true;
		return false;
	}

	private static boolean isIdentifierContinue(int ch) {
		if (isIdentifierStart(ch))
			return true;
		if (ch >= '0' && ch <= '9')
			return true;
		if (ch == '_')
			return true;
		return false;
	}

	private static boolean isCodeIdentifierStart(int ch) {
		if (ch >= 'a' && ch <= 'z')
			return true;
		if (ch >= 'A' && ch <= 'Z')
			return true;
		if (ch == '_')
			return true;
		return false;
	}

	private static boolean isCodeIdentifierContinue(int ch) {
		if (isCodeIdentifierStart(ch))
			return true;
		if (ch >= '0' && ch <= '9')
			return true;
		return false;
	}

	private static boolean isDigit(int ch) {
		if (ch >= '0' && ch <= '9')
			return true;
		return false;
	}

	private IToken parseCodeStringOrChar(boolean gotOpeningQuote) {
		int quote = '\"';
		if (!gotOpeningQuote) {
			Assert.isTrue(peek() == '\'' || peek() == '\"');
			quote = peek();
			tokenLength += next();
		}
		while (peek() != quote && peek() != EOF && peek() != '\r' && peek() != '\n') {
			if (peek() == '\\') {
				tokenLength += next();
				if (peek() == EOF)
					break;
				tokenLength += next();
			} else {
				tokenLength += next();
			}
		}
		if (peek() == quote) {
			tokenLength += next();
			if (isCodeIdentifierStart(peek())) {
				do
					tokenLength += next();
				while (isCodeIdentifierContinue(peek()));
			}
		}
		if (quote == '\'')
			return codeCharToken;
		return codeStringToken;
	}

	private <T> boolean isSameSequence(Iterable<T> a, Iterable<T> b) {
		Iterator<T> aIterator = a.iterator();
		Iterator<T> bIterator = b.iterator();
		while (true) {
			if (!aIterator.hasNext())
				return !bIterator.hasNext();
			if (!bIterator.hasNext())
				return false;
			T aValue = aIterator.next();
			T bValue = bIterator.next();
			if (aValue == null ? bValue != null : !aValue.equals(bValue))
				return false;
		}
	}

	private IToken nextCodeTokenInternal() {
		if (isCodeWhitespace(peek())) {
			do {
				if (peek() == '\r' || peek() == '\n')
					includeState = IncludeState.StartOfLine;
				tokenLength += next();
			} while (isCodeWhitespace(peek()));
			return Token.WHITESPACE;
		}
		if (peek() == '#') {
			tokenLength += next();
			if (peek() == '#') {
				tokenLength += next();
				includeState = IncludeState.Other;
			} else if (includeState == IncludeState.StartOfLine) {
				includeState = IncludeState.GotPound;
			}
			return codePunctuatorToken;
		}
		if (includeState == IncludeState.GotInclude && (peek() == '<' || peek() == '\"')) {
			int terminator = peek() == '<' ? '>' : '\"';
			do {
				tokenLength += next();
			} while (peek() != terminator && peek() != EOF && peek() != '\r' && peek() != '\n');
			includeState = IncludeState.Other;
			return codeHeaderNameToken;
		}
		if (peek() == '/') {
			tokenLength += next();
			if (peek() == '*') {
				tokenLength += next();
				boolean wasLastStar = false;
				while (!wasLastStar || peek() != '/') {
					if (peek() == EOF)
						break;
					wasLastStar = peek() == '*';
					tokenLength += next();
				}
				tokenLength += next();
				return codeBlockCommentToken;
			}
			includeState = IncludeState.Other;
			if (peek() == '/') {
				while (peek() != '\r' && peek() != '\n' && peek() != EOF) {
					tokenLength += next();
				}
				return codeLineCommentToken;
			}
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		}
		if (isCodeIdentifierStart(peek())) {
			StringBuilder valueBuilder = new StringBuilder();
			do {
				valueBuilder.appendCodePoint(peek());
				tokenLength += next();
			} while (isCodeIdentifierContinue(peek()));
			String value = valueBuilder.toString();
			if (includeState == IncludeState.GotPound) {
				if (value.equals("include")) {
					includeState = IncludeState.GotInclude;
					return codeKeywordToken;
				}
				includeState = IncludeState.Other;
				if (value.equals("define"))
					return codeKeywordToken;
				if (value.equals("undef"))
					return codeKeywordToken;
				if (value.equals("if"))
					return codeKeywordToken;
				if (value.equals("ifdef"))
					return codeKeywordToken;
				if (value.equals("ifndef"))
					return codeKeywordToken;
				if (value.equals("else"))
					return codeKeywordToken;
				if (value.equals("elif"))
					return codeKeywordToken;
				if (value.equals("endif"))
					return codeKeywordToken;
				if (value.equals("line"))
					return codeKeywordToken;
				if (value.equals("error"))
					return codeKeywordToken;
				if (value.equals("pragma"))
					return codeKeywordToken;
				if (value.equals("warning"))
					return codeKeywordToken;
				return codeIdentifierToken;
			}
			includeState = IncludeState.Other;
			if (peek() == '\'' || peek() == '\"') {
				if (value.equals("u") || value.equals("U") || value.equals("u8") || value.equals("L")) {
					return parseCodeStringOrChar(false);
				}
			}
			if (peek() == '\"') {
				if (value.endsWith("R")) {
					tokenLength += next();
					ArrayList<Integer> seperator = new ArrayList<>();
					seperator.add((int) ')');
					while (peek() != '(' && peek() != ')' && peek() != '\"' && peek() != EOF && peek() != ' '
							&& peek() != ' ' && peek() != '\\' && peek() != '\t' && peek() != '\r' && peek() != '\n') {
						seperator.add(peek());
						tokenLength += next();
					}
					if (peek() != '(') {
						return parseCodeStringOrChar(true);
					}
					tokenLength += next();
					seperator.add((int) '\"');
					ArrayDeque<Integer> recentCharacters = new ArrayDeque<>(seperator.size());
					while (peek() != EOF && !isSameSequence(seperator, recentCharacters)) {
						if (recentCharacters.size() >= seperator.size())
							recentCharacters.removeFirst();
						recentCharacters.add(peek());
						tokenLength += next();
					}
					if (isCodeIdentifierStart(peek())) {
						do
							tokenLength += next();
						while (isCodeIdentifierContinue(peek()));
					}
					return codeStringToken;
				}
			}
			if (value.equals("defined"))
				return codeKeywordToken;
			if (value.equals("__has_include"))
				return codeKeywordToken;
			if (value.equals("alignas"))
				return codeKeywordToken;
			if (value.equals("alignof"))
				return codeKeywordToken;
			if (value.equals("and"))
				return codeKeywordToken;
			if (value.equals("and_eq"))
				return codeKeywordToken;
			if (value.equals("asm"))
				return codeKeywordToken;
			if (value.equals("atomic_cancel"))
				return codeKeywordToken;
			if (value.equals("atomic_commit"))
				return codeKeywordToken;
			if (value.equals("atomic_noexcept"))
				return codeKeywordToken;
			if (value.equals("auto"))
				return codeKeywordToken;
			if (value.equals("bitand"))
				return codeKeywordToken;
			if (value.equals("bitor"))
				return codeKeywordToken;
			if (value.equals("bool"))
				return codeKeywordToken;
			if (value.equals("break"))
				return codeKeywordToken;
			if (value.equals("case"))
				return codeKeywordToken;
			if (value.equals("catch"))
				return codeKeywordToken;
			if (value.equals("char"))
				return codeKeywordToken;
			if (value.equals("char16_t"))
				return codeKeywordToken;
			if (value.equals("char32_t"))
				return codeKeywordToken;
			if (value.equals("class"))
				return codeKeywordToken;
			if (value.equals("compl"))
				return codeKeywordToken;
			if (value.equals("concept"))
				return codeKeywordToken;
			if (value.equals("const"))
				return codeKeywordToken;
			if (value.equals("constexpr"))
				return codeKeywordToken;
			if (value.equals("const_cast"))
				return codeKeywordToken;
			if (value.equals("continue"))
				return codeKeywordToken;
			if (value.equals("decltype"))
				return codeKeywordToken;
			if (value.equals("default"))
				return codeKeywordToken;
			if (value.equals("delete"))
				return codeKeywordToken;
			if (value.equals("do"))
				return codeKeywordToken;
			if (value.equals("double"))
				return codeKeywordToken;
			if (value.equals("dynamic_cast"))
				return codeKeywordToken;
			if (value.equals("else"))
				return codeKeywordToken;
			if (value.equals("enum"))
				return codeKeywordToken;
			if (value.equals("explicit"))
				return codeKeywordToken;
			if (value.equals("export"))
				return codeKeywordToken;
			if (value.equals("extern"))
				return codeKeywordToken;
			if (value.equals("false"))
				return codeKeywordToken;
			if (value.equals("float"))
				return codeKeywordToken;
			if (value.equals("for"))
				return codeKeywordToken;
			if (value.equals("friend"))
				return codeKeywordToken;
			if (value.equals("goto"))
				return codeKeywordToken;
			if (value.equals("if"))
				return codeKeywordToken;
			if (value.equals("inline"))
				return codeKeywordToken;
			if (value.equals("int"))
				return codeKeywordToken;
			if (value.equals("import"))
				return codeKeywordToken;
			if (value.equals("long"))
				return codeKeywordToken;
			if (value.equals("module"))
				return codeKeywordToken;
			if (value.equals("mutable"))
				return codeKeywordToken;
			if (value.equals("namespace"))
				return codeKeywordToken;
			if (value.equals("new"))
				return codeKeywordToken;
			if (value.equals("noexcept"))
				return codeKeywordToken;
			if (value.equals("not"))
				return codeKeywordToken;
			if (value.equals("not_eq"))
				return codeKeywordToken;
			if (value.equals("nullptr"))
				return codeKeywordToken;
			if (value.equals("operator"))
				return codeKeywordToken;
			if (value.equals("or"))
				return codeKeywordToken;
			if (value.equals("or_eq"))
				return codeKeywordToken;
			if (value.equals("private"))
				return codeKeywordToken;
			if (value.equals("protected"))
				return codeKeywordToken;
			if (value.equals("public"))
				return codeKeywordToken;
			if (value.equals("register"))
				return codeKeywordToken;
			if (value.equals("reinterpret_cast"))
				return codeKeywordToken;
			if (value.equals("requires"))
				return codeKeywordToken;
			if (value.equals("return"))
				return codeKeywordToken;
			if (value.equals("short"))
				return codeKeywordToken;
			if (value.equals("signed"))
				return codeKeywordToken;
			if (value.equals("sizeof"))
				return codeKeywordToken;
			if (value.equals("static"))
				return codeKeywordToken;
			if (value.equals("static_assert"))
				return codeKeywordToken;
			if (value.equals("static_cast"))
				return codeKeywordToken;
			if (value.equals("struct"))
				return codeKeywordToken;
			if (value.equals("switch"))
				return codeKeywordToken;
			if (value.equals("synchronized"))
				return codeKeywordToken;
			if (value.equals("template"))
				return codeKeywordToken;
			if (value.equals("this"))
				return codeKeywordToken;
			if (value.equals("thread_local"))
				return codeKeywordToken;
			if (value.equals("throw"))
				return codeKeywordToken;
			if (value.equals("true"))
				return codeKeywordToken;
			if (value.equals("try"))
				return codeKeywordToken;
			if (value.equals("typedef"))
				return codeKeywordToken;
			if (value.equals("typeid"))
				return codeKeywordToken;
			if (value.equals("typename"))
				return codeKeywordToken;
			if (value.equals("union"))
				return codeKeywordToken;
			if (value.equals("unsigned"))
				return codeKeywordToken;
			if (value.equals("using"))
				return codeKeywordToken;
			if (value.equals("virtual"))
				return codeKeywordToken;
			if (value.equals("void"))
				return codeKeywordToken;
			if (value.equals("volatile"))
				return codeKeywordToken;
			if (value.equals("wchar_t"))
				return codeKeywordToken;
			if (value.equals("while"))
				return codeKeywordToken;
			if (value.equals("xor"))
				return codeKeywordToken;
			if (value.equals("xor_eq"))
				return codeKeywordToken;
			if (value.equals("override"))
				return codeKeywordToken;
			if (value.equals("final"))
				return codeKeywordToken;
			if (value.equals("transaction_safe"))
				return codeKeywordToken;
			if (value.equals("transaction_safe_dynamic"))
				return codeKeywordToken;
			if (value.equals("__attribute__"))
				return codeKeywordToken;
			if (value.equals("__declspec"))
				return codeKeywordToken;
			if (value.equals("__restrict"))
				return codeKeywordToken;
			if (value.equals("_Pragma"))
				return codeKeywordToken;
			if (value.equals("__asm"))
				return codeKeywordToken;
			if (value.equals("__extension__"))
				return codeKeywordToken;
			return codeIdentifierToken;
		}
		includeState = IncludeState.Other;
		if (peek() == '.' || isDigit(peek())) {
			if (peek() == '.') {
				tokenLength += next();
				if (peek() == '.') {
					tokenLength += next();
					if (peek() == '.') {
						tokenLength += next();
					} else
						backup('.');
					return codePunctuatorToken;
				}
				if (peek() == '*') {
					tokenLength += next();
					return codePunctuatorToken;
				}
				if (!isDigit(peek()))
					return codePunctuatorToken;
			} else {
				tokenLength += next();
			}
			while (isDigit(peek()) || peek() == 'e' || peek() == 'E' || isCodeIdentifierContinue(peek())
					|| peek() == '.') {
				if (peek() == 'e' || peek() == 'E') {
					tokenLength += next();
					if (peek() == '-' || peek() == '+')
						tokenLength += next();
					continue;
				}
				tokenLength += next();
			}
			return codeNumberToken;
		}
		switch (peek()) {
		case '$':
			tokenLength += next();
			if (peek() == '$' || peek() == EOF || peek() == '?') {
				tokenLength += next();
				return substitutionToken;
			}
			return codeUnknownToken;
		case '\'':
		case '\"':
			return parseCodeStringOrChar(false);
		case '{':
			codeNestDepth++;
			tokenLength += next();
			return codePunctuatorToken;
		case '}':
			codeNestDepth--;
			tokenLength += next();
			if (codeNestDepth == 0)
				return codeToken;
			return codePunctuatorToken;
		case '/':
			tokenLength += next();
			if (peek() == '*') {
				tokenLength += next();
				boolean wasLastStar = false;
				while (!wasLastStar || peek() != '/') {
					if (peek() == EOF)
						break;
					wasLastStar = peek() == '*';
					tokenLength += next();
				}
				tokenLength += next();
				return codeBlockCommentToken;
			}
			if (peek() == '/') {
				while (peek() != '\r' && peek() != '\n' && peek() != EOF) {
					tokenLength += next();
				}
				return codeLineCommentToken;
			}
			if (peek() == '=') {
				tokenLength += next();
			}
			return codePunctuatorToken;
		case '[':
		case ']':
		case '(':
		case ')':
		case ',':
		case ';':
		case '?':
		case '~':
			tokenLength += next();
			return codePunctuatorToken;
		case '<':
			tokenLength += next();
			if (peek() == ':') {
				tokenLength += next();
				if (peek() == ':') {
					tokenLength += next();
					if (peek() == ':' || peek() == '>') {
						backup(':');
						return codePunctuatorToken;
					}
					backup(':');
					backup(':');
					return codePunctuatorToken;
				}
				return codePunctuatorToken;
			}
			if (peek() == '%') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			if (peek() == '<') {
				tokenLength += next();
				if (peek() == '=') {
					tokenLength += next();
					return codePunctuatorToken;
				}
				return codePunctuatorToken;
			}
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case ':':
			tokenLength += next();
			if (peek() == '>') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			if (peek() == ':') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '%':
			tokenLength += next();
			if (peek() == '>') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			if (peek() == ':') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '+':
			tokenLength += next();
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			if (peek() == '+') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '-':
			tokenLength += next();
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			if (peek() == '-') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			if (peek() == '>') {
				tokenLength += next();
				if (peek() == '*') {
					tokenLength += next();
					return codePunctuatorToken;
				}
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '*':
			tokenLength += next();
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '^':
			tokenLength += next();
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '&':
			tokenLength += next();
			if (peek() == '&') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '|':
			tokenLength += next();
			if (peek() == '|') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '!':
			tokenLength += next();
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '=':
			tokenLength += next();
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		case '>':
			tokenLength += next();
			if (peek() == '>') {
				tokenLength += next();
				if (peek() == '=') {
					tokenLength += next();
					return codePunctuatorToken;
				}
				return codePunctuatorToken;
			}
			if (peek() == '=') {
				tokenLength += next();
				return codePunctuatorToken;
			}
			return codePunctuatorToken;
		default:
			tokenLength += next();
			return codeUnknownToken;
		}
	}

	private IToken nextTokenInternal() {
		tokenOffset = inputOffset;
		tokenLength = 0;
		if (peek() == EOF)
			return Token.EOF;
		if (codeNestDepth > 0)
			return nextCodeTokenInternal();
		if (isWhitespace(peek())) {
			do {
				tokenLength += next();
			} while (isWhitespace(peek()));
			return Token.WHITESPACE;
		}
		if (isIdentifierStart(peek())) {
			StringBuilder valueBuilder = new StringBuilder();
			do {
				valueBuilder.appendCodePoint(peek());
				tokenLength += next();
			} while (isIdentifierContinue(peek()));
			String value = valueBuilder.toString();
			if (value.equals("EOF"))
				return keywordToken;
			if (value.equals("typedef"))
				return keywordToken;
			if (value.equals("code"))
				return keywordToken;
			if (value.equals("namespace"))
				return keywordToken;
			if (value.equals("false"))
				return keywordToken;
			if (value.equals("true"))
				return keywordToken;
			return ruleNameToken;
		}
		switch (peek()) {
		case '/':
			tokenLength += next();
			if (peek() == '*') {
				tokenLength += next();
				boolean wasLastStar = false;
				while (!wasLastStar || peek() != '/') {
					if (peek() == EOF)
						break;
					wasLastStar = peek() == '*';
					tokenLength += next();
				}
				tokenLength += next();
				return blockCommentToken;
			}
			if (peek() == '/') {
				while (peek() != '\r' && peek() != '\n' && peek() != EOF) {
					tokenLength += next();
				}
				return lineCommentToken;
			}
			return operatorToken;
		case '\"':
			tokenLength += next();
			while (peek() != EOF && peek() != '\"' && peek() != '\r' && peek() != '\n') {
				if (peek() == '\\') {
					tokenLength += next();
					if (peek() == EOF || peek() == '\r' || peek() == '\n')
						break;
					tokenLength += next();
				} else {
					tokenLength += next();
				}
			}
			if (peek() == '\"')
				tokenLength += next();
			return stringToken;
		case '[':
			tokenLength += next();
			while (peek() != EOF && peek() != ']' && peek() != '\r' && peek() != '\n') {
				if (peek() == '\\') {
					tokenLength += next();
					if (peek() == EOF || peek() == '\r' || peek() == '\n')
						break;
					tokenLength += next();
				} else {
					tokenLength += next();
				}
			}
			if (peek() == ']')
				tokenLength += next();
			return characterClassToken;
		case '{':
			tokenLength += next();
			codeNestDepth = 1;
			includeState = IncludeState.StartOfLine;
			return codeToken;
		case ';':
		case '?':
		case '+':
		case '!':
		case '*':
		case '=':
		case '(':
		case ')':
		case '&':
		case '<':
		case '>':
		case ',':
			tokenLength += next();
			return operatorToken;
		default:
			tokenLength += next();
			return Token.UNDEFINED;
		}
	}

	@Override
	public IToken nextToken() {
		IToken retval;
		while (true) {
			retval = nextTokenInternal();
			if (tokenOffset + tokenLength <= inputBegin)
				continue;
			if (tokenOffset < inputBegin) {
				tokenLength -= inputBegin - tokenOffset;
				tokenOffset = inputBegin;
			}
			break;
		}
		return retval;
	}

	@Override
	public void setRange(IDocument document, int offset, int length) {
		Assert.isNotNull(document);
		Assert.isLegal(offset >= 0);
		Assert.isLegal(length >= 0);
		Assert.isLegal(offset + length <= document.getLength());
		inputOffset = 0; // always start from beginning
		codeNestDepth = 0;
		includeState = IncludeState.StartOfLine;
		inputBegin = offset;
		inputEnd = offset + length;
		inputDocument = document;
	}
}
