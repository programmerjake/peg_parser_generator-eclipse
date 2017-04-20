package tk.programmerjake.peg_parser_generator.eclipse.editors;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;

final class PEGParser {

	private final PEGSyntaxHighlightingConstants syntaxHighlightingConstants;

	public PEGParser(ColorManager manager) {
		syntaxHighlightingConstants = new PEGSyntaxHighlightingConstants(manager);
	}

	public ITokenScanner getTokenScanner() {
		return new ITokenScanner() {

			private Iterable<Token> tokens;
			private Iterator<Token> tokenIterator;
			private Token lastToken;
			private int offset;
			private int length;
			private IDocument document;

			@Override
			public void setRange(IDocument document, int offset, int length) {
				this.document = document;
				this.offset = offset;
				this.length = length;
				tokens = null;
				tokenIterator = null;
				lastToken = null;
			}

			@Override
			public IToken nextToken() {
				if (lastToken == null) {
					tokens = parse(document);
					tokenIterator = tokens.iterator();
					lastToken = tokenIterator.next();
				} else if (!lastToken.isEOF())
					lastToken = tokenIterator.next();
				while (!lastToken.isEOF() && lastToken.offset + lastToken.length <= offset) {
					lastToken = tokenIterator.next();
				}
				return lastToken;
			}

			@Override
			public int getTokenOffset() {
				if (lastToken.offset < offset)
					return offset;
				if (lastToken.offset > offset + length)
					return offset + length;
				return lastToken.offset;
			}

			@Override
			public int getTokenLength() {
				int tokenOffset = lastToken.offset;
				int tokenLength = lastToken.length;
				if (tokenOffset < offset) {
					tokenLength -= offset - tokenOffset;
					tokenOffset = offset;
				}
				if (tokenOffset > offset + length)
					return 0;
				if (tokenOffset + tokenLength > offset + length)
					tokenLength = offset + length - tokenOffset;
				return tokenLength;
			}
		};
	}

	private enum TokenType {
		EndOfFile(false),
		Whitespace(false),
		LineComment(false),
		BlockComment(false),
		Semicolon(false),
		Colon(false),
		ColonColon(false),
		QMark(false),
		Plus(false),
		EMark(false),
		Star(false),
		FSlash(false),
		Equal(false),
		LParen(false),
		RParen(false),
		LAngle(false),
		RAngle(false),
		Amp(false),
		Comma(false),
		String(false),
		Identifier(true),
		EOFKeyword(false),
		TypedefKeyword(false),
		NamespaceKeyword(false),
		CodeKeyword(false),
		FalseKeyword(false),
		TrueKeyword(false),
		CharacterClass(false),
		CodeSnippetStart(false),
		CodeSnippetEnd(false),
		CodeSnippetSubstitution(false),
		CodeSnippetLineComment(false),
		CodeSnippetBlockComment(false),
		CodeSnippetKeyword(false),
		CodeSnippetIdentifier(true),
		CodeSnippetString(false),
		CodeSnippetChar(false),
		CodeSnippetHeaderName(false),
		CodeSnippetNumber(false),
		CodeSnippetPunctuator(false),
		CodeSnippetUnknown(false),
		Unknown(false);
		public final boolean valueRequired;

		private TokenType(boolean valueRequired) {
			this.valueRequired = valueRequired;
		}
	}

	static private class Token implements IToken {
		public final TokenType type;
		public final int offset;
		public final int length;
		public TextAttribute style;
		public final String value;

		public Token(TokenType type, int offset, int length, TextAttribute style, String value) {
			this.type = type;
			this.offset = offset;
			this.length = length;
			this.style = style;
			this.value = value;
			Assert.isLegal(!type.valueRequired || value != null);
		}

		@Override
		public Object getData() {
			return style;
		}

		@Override
		public boolean isEOF() {
			return type == TokenType.EndOfFile;
		}

		@Override
		public boolean isOther() {
			return !isEOF() && !isUndefined() && !isWhitespace();
		}

		@Override
		public boolean isUndefined() {
			return type == null;
		}

		@Override
		public boolean isWhitespace() {
			return type == TokenType.Whitespace;
		}
	}

	private static final class Tokenizer {

		private static final int EOF = -1;

		private enum IncludeState {
			StartOfLine,
			GotPound,
			GotInclude,
			Other,
		}

		private final PEGSyntaxHighlightingConstants syntaxHighlightingConstants;

		public Tokenizer(PEGSyntaxHighlightingConstants syntaxHighlightingConstants) {
			this.syntaxHighlightingConstants = syntaxHighlightingConstants;
		}

		private IDocument document;
		private int documentLength;
		private int currentPosition;
		private int peek;
		private int codeNestDepth;
		private IncludeState includeState;
		private Token lastPoundToken;

		private int readCurrentCharacter() {
			if (currentPosition >= documentLength) {
				return EOF;
			}
			try {
				char firstChar = document.getChar(currentPosition);
				if (Character.isHighSurrogate(firstChar) && currentPosition + 1 < documentLength) {
					try {
						char secondChar = document.getChar(currentPosition + 1);
						if (Character.isLowSurrogate(secondChar)) {
							return Character.toCodePoint(firstChar, secondChar);
						}
					} catch (BadLocationException e) {
					}
				}
				return firstChar;
			} catch (BadLocationException e) {
				return EOF;
			}
		}

		private int get() {
			int retval = peek;
			if (retval != EOF) {
				currentPosition += Character.charCount(retval);
				peek = readCurrentCharacter();
			}
			return retval;
		}

		private void backup(int ch) {
			Assert.isLegal(ch != EOF);
			currentPosition -= Character.charCount(ch);
			Assert.isLegal(currentPosition >= 0);
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

		private Token parseCodeStringOrChar(boolean gotOpeningQuote, final int tokenStartPosition) {
			int quote = '\"';
			if (!gotOpeningQuote) {
				Assert.isTrue(peek == '\'' || peek == '\"');
				quote = peek;
				get();
			}
			while (peek != quote && peek != EOF && peek != '\r' && peek != '\n') {
				if (peek == '\\') {
					get();
					if (peek == EOF)
						break;
					get();
				} else {
					get();
				}
			}
			if (peek == quote) {
				get();
				if (isCodeIdentifierStart(peek)) {
					do
						get();
					while (isCodeIdentifierContinue(peek));
				}
			}
			if (quote == '\'')
				return new Token(TokenType.CodeSnippetChar, tokenStartPosition, currentPosition - tokenStartPosition,
						syntaxHighlightingConstants.codeCharTextAttribute, null);
			return new Token(TokenType.CodeSnippetString, tokenStartPosition, currentPosition - tokenStartPosition,
					syntaxHighlightingConstants.codeStringTextAttribute, null);
		}

		private static <T> boolean isSameSequence(Iterable<T> a, Iterable<T> b) {
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

		private Token nextCodeTokenInternal(final int tokenStartPosition) {
			if (isCodeWhitespace(peek)) {
				do {
					if (peek == '\r' || peek == '\n')
						includeState = IncludeState.StartOfLine;
					get();
				} while (isCodeWhitespace(peek));
				return new Token(TokenType.Whitespace, tokenStartPosition, currentPosition - tokenStartPosition, null,
						null);
			}
			if (peek == '#') {
				get();
				if (peek == '#') {
					get();
					includeState = IncludeState.Other;
				} else if (includeState == IncludeState.StartOfLine) {
					includeState = IncludeState.GotPound;
				}
				lastPoundToken = new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
				return lastPoundToken;
			}
			if (includeState == IncludeState.GotInclude && (peek == '<' || peek == '\"')) {
				int terminator = peek == '<' ? '>' : '\"';
				do {
					get();
				} while (peek != terminator && peek != EOF && peek != '\r' && peek != '\n');
				if (peek == terminator)
					get();
				includeState = IncludeState.Other;
				return new Token(TokenType.CodeSnippetHeaderName, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeHeaderNameTextAttribute,
						null);
			}
			if (peek == '/') {
				get();
				if (peek == '*') {
					get();
					boolean wasLastStar = false;
					while (!wasLastStar || peek != '/') {
						if (peek == EOF)
							break;
						wasLastStar = peek == '*';
						get();
					}
					get();
					return new Token(TokenType.CodeSnippetBlockComment, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codeBlockCommentTextAttribute, null);
				}
				includeState = IncludeState.Other;
				if (peek == '/') {
					while (peek != '\r' && peek != '\n' && peek != EOF) {
						get();
					}
					return new Token(TokenType.CodeSnippetLineComment, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codeLineCommentTextAttribute, null);
				}
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			}
			if (isCodeIdentifierStart(peek)) {
				StringBuilder valueBuilder = new StringBuilder();
				do {
					valueBuilder.appendCodePoint(get());
				} while (isCodeIdentifierContinue(peek));
				String value = valueBuilder.toString();
				if (includeState == IncludeState.GotPound) {
					lastPoundToken.style = syntaxHighlightingConstants.keywordTextAttribute;
					if (value.equals("include")) {
						includeState = IncludeState.GotInclude;
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					}
					includeState = IncludeState.Other;
					if (value.equals("define"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("undef"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("if"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("ifdef"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("ifndef"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("else"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("elif"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("endif"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("line"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("error"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("pragma"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					if (value.equals("warning"))
						return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeKeywordTextAttribute, null);
					return new Token(TokenType.CodeSnippetIdentifier, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codeIdentifierTextAttribute, value);
				}
				includeState = IncludeState.Other;
				if (peek == '\'' || peek == '\"') {
					if (value.equals("u") || value.equals("U") || value.equals("u8") || value.equals("L")) {
						return parseCodeStringOrChar(false, tokenStartPosition);
					}
				}
				if (peek == '\"') {
					if (value.endsWith("R")) {
						get();
						ArrayList<Integer> seperator = new ArrayList<Integer>();
						seperator.add((int) ')');
						while (peek != '(' && peek != ')' && peek != '\"' && peek != EOF && peek != ' ' && peek != ' '
								&& peek != '\\' && peek != '\t' && peek != '\r' && peek != '\n') {
							seperator.add(get());
						}
						if (peek != '(') {
							return parseCodeStringOrChar(true, tokenStartPosition);
						}
						get();
						seperator.add((int) '\"');
						ArrayDeque<Integer> recentCharacters = new ArrayDeque<Integer>(seperator.size());
						while (peek != EOF && !isSameSequence(seperator, recentCharacters)) {
							if (recentCharacters.size() >= seperator.size())
								recentCharacters.removeFirst();
							recentCharacters.add(get());
						}
						if (isCodeIdentifierStart(peek)) {
							do
								get();
							while (isCodeIdentifierContinue(peek));
						}
						return new Token(TokenType.CodeSnippetString, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codeStringTextAttribute, null);
					}
				}
				if (value.equals("defined"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("__has_include"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("alignas"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("alignof"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("and"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("and_eq"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("asm"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("atomic_cancel"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("atomic_commit"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("atomic_noexcept"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("auto"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("bitand"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("bitor"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("bool"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("break"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("case"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("catch"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("char"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("char16_t"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("char32_t"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("class"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("compl"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("concept"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("const"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("constexpr"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("const_cast"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("continue"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("decltype"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("default"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("delete"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("do"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("double"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("dynamic_cast"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("else"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("enum"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("explicit"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("export"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("extern"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("false"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("float"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("for"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("friend"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("goto"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("if"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("inline"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("int"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("import"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("long"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("module"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("mutable"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("namespace"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("new"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("noexcept"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("not"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("not_eq"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("nullptr"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("operator"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("or"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("or_eq"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("private"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("protected"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("public"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("register"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("reinterpret_cast"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("requires"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("return"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("short"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("signed"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("sizeof"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("static"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("static_assert"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("static_cast"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("struct"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("switch"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("synchronized"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("template"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("this"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("thread_local"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("throw"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("true"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("try"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("typedef"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("typeid"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("typename"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("union"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("unsigned"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("using"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("virtual"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("void"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("volatile"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("wchar_t"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("while"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("xor"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("xor_eq"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("override"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("final"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("transaction_safe"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("transaction_safe_dynamic"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("__attribute__"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("__declspec"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("__restrict"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("_Pragma"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("__asm"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				if (value.equals("__extension__"))
					return new Token(TokenType.CodeSnippetKeyword, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeKeywordTextAttribute,
							null);
				return new Token(TokenType.CodeSnippetIdentifier, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeIdentifierTextAttribute,
						value);
			}
			includeState = IncludeState.Other;
			if (peek == '.' || isDigit(peek)) {
				if (peek == '.') {
					get();
					if (peek == '.') {
						get();
						if (peek == '.') {
							get();
						} else
							backup('.');
						return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
					}
					if (peek == '*') {
						get();
						return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
					}
					if (!isDigit(peek))
						return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				} else {
					get();
				}
				while (isDigit(peek) || peek == 'e' || peek == 'E' || isCodeIdentifierContinue(peek) || peek == '.') {
					if (peek == 'e' || peek == 'E') {
						get();
						if (peek == '-' || peek == '+')
							get();
						continue;
					}
					get();
				}
				return new Token(TokenType.CodeSnippetNumber, tokenStartPosition, currentPosition - tokenStartPosition,
						syntaxHighlightingConstants.codeNumberTextAttribute, null);
			}
			switch (peek) {
			case '$':
				get();
				if (peek == '$' || peek == '_' || peek == '?') {
					get();
					return new Token(TokenType.CodeSnippetSubstitution, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.substitutionTextAttribute,
							null);
				}
				return new Token(TokenType.CodeSnippetUnknown, tokenStartPosition, currentPosition - tokenStartPosition,
						syntaxHighlightingConstants.codeUnknownTextAttribute, null);
			case '\'':
			case '\"':
				return parseCodeStringOrChar(false, tokenStartPosition);
			case '{':
				codeNestDepth++;
				get();
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '}':
				codeNestDepth--;
				get();
				if (codeNestDepth == 0)
					return new Token(TokenType.CodeSnippetEnd, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codeTextAttribute, null);
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '[':
			case ']':
			case '(':
			case ')':
			case ',':
			case ';':
			case '?':
			case '~':
				get();
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '<':
				get();
				if (peek == ':') {
					get();
					if (peek == ':') {
						get();
						if (peek == ':' || peek == '>') {
							backup(':');
							return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
									currentPosition - tokenStartPosition,
									syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
						}
						backup(':');
						backup(':');
						return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
					}
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '%') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '<') {
					get();
					if (peek == '=') {
						get();
						return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
					}
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case ':':
				get();
				if (peek == '>') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == ':') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '%':
				get();
				if (peek == '>') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == ':') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '+':
				get();
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '+') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '-':
				get();
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '-') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '>') {
					get();
					if (peek == '*') {
						get();
						return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
					}
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '*':
				get();
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '^':
				get();
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '&':
				get();
				if (peek == '&') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '|':
				get();
				if (peek == '|') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '!':
				get();
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '=':
				get();
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			case '>':
				get();
				if (peek == '>') {
					get();
					if (peek == '=') {
						get();
						return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
					}
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				if (peek == '=') {
					get();
					return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.codePunctuatorTextAttribute, null);
				}
				return new Token(TokenType.CodeSnippetPunctuator, tokenStartPosition,
						currentPosition - tokenStartPosition, syntaxHighlightingConstants.codePunctuatorTextAttribute,
						null);
			default:
				get();
				return new Token(TokenType.CodeSnippetUnknown, tokenStartPosition, currentPosition - tokenStartPosition,
						syntaxHighlightingConstants.codeUnknownTextAttribute, null);
			}
		}

		public List<Token> tokenize(IDocument document) {
			this.document = document;
			documentLength = document.getLength();
			currentPosition = 0;
			peek = readCurrentCharacter();
			final List<Token> tokens = new ArrayList<Token>();
			while (peek != EOF) {
				int tokenStartPosition = currentPosition;
				if (isWhitespace(peek)) {
					while (isWhitespace(peek)) {
						get();
					}
					tokens.add(new Token(TokenType.Whitespace, tokenStartPosition, currentPosition - tokenStartPosition,
							null, null));
					continue;
				}
				if (isIdentifierStart(peek)) {
					StringBuilder valueBuilder = new StringBuilder();
					do {
						valueBuilder.appendCodePoint(get());
					} while (isIdentifierContinue(peek));
					String value = valueBuilder.toString();
					if (value.equals("EOF"))
						tokens.add(new Token(TokenType.EOFKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition, syntaxHighlightingConstants.keywordTextAttribute,
								null));
					else if (value.equals("typedef"))
						tokens.add(new Token(TokenType.TypedefKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition, syntaxHighlightingConstants.keywordTextAttribute,
								null));
					else if (value.equals("code"))
						tokens.add(new Token(TokenType.CodeKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition, syntaxHighlightingConstants.keywordTextAttribute,
								null));
					else if (value.equals("namespace"))
						tokens.add(new Token(TokenType.NamespaceKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition, syntaxHighlightingConstants.keywordTextAttribute,
								null));
					else if (value.equals("false"))
						tokens.add(new Token(TokenType.FalseKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition, syntaxHighlightingConstants.keywordTextAttribute,
								null));
					else if (value.equals("true"))
						tokens.add(new Token(TokenType.TrueKeyword, tokenStartPosition,
								currentPosition - tokenStartPosition, syntaxHighlightingConstants.keywordTextAttribute,
								null));
					else
						tokens.add(new Token(TokenType.Identifier, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.identifierTextAttribute, value));

					continue;
				}
				switch (peek) {
				case '/':
					get();
					if (peek == '*') {
						get();
						boolean wasLastStar = false;
						while (peek != EOF) {
							if (peek == '/' && wasLastStar) {
								get();
								break;
							}
							wasLastStar = get() == '*';
						}
						tokens.add(new Token(TokenType.BlockComment, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.blockCommentTextAttribute, null));
						continue;
					}
					if (peek == '/') {
						while (peek != '\r' && peek != '\n' && peek != EOF) {
							get();
						}
						tokens.add(new Token(TokenType.LineComment, tokenStartPosition,
								currentPosition - tokenStartPosition,
								syntaxHighlightingConstants.lineCommentTextAttribute, null));
						continue;
					}
					tokens.add(new Token(TokenType.FSlash, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '{':
					get();
					codeNestDepth = 1;
					includeState = IncludeState.StartOfLine;
					tokens.add(new Token(TokenType.CodeSnippetStart, tokenStartPosition,
							currentPosition - tokenStartPosition, syntaxHighlightingConstants.codeTextAttribute, null));
					while (codeNestDepth > 0 && peek != EOF) {
						tokens.add(nextCodeTokenInternal(currentPosition));
					}
					continue;
				case '\"':
					get();
					while (peek != EOF && peek != '\"' && peek != '\r' && peek != '\n') {
						if (peek == '\\') {
							get();
							if (peek == EOF || peek == '\r' || peek == '\n')
								break;
							get();
						} else {
							get();
						}
					}
					if (peek == '\"')
						get();
					tokens.add(new Token(TokenType.String, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.stringTextAttribute, null));
					continue;
				case '[':
					get();
					while (peek != EOF && peek != ']' && peek != '\r' && peek != '\n') {
						if (peek == '\\') {
							get();
							if (peek == EOF || peek == '\r' || peek == '\n')
								break;
							get();
						} else {
							get();
						}
					}
					if (peek == ']')
						get();
					tokens.add(new Token(TokenType.CharacterClass, tokenStartPosition,
							currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.characterClassTextAttribute, null));
					continue;
				case ';':
					get();
					tokens.add(new Token(TokenType.Semicolon, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '?':
					get();
					tokens.add(new Token(TokenType.QMark, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '+':
					get();
					tokens.add(new Token(TokenType.Plus, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '!':
					get();
					tokens.add(new Token(TokenType.EMark, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '*':
					get();
					tokens.add(new Token(TokenType.Star, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '=':
					get();
					tokens.add(new Token(TokenType.Equal, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '(':
					get();
					tokens.add(new Token(TokenType.LParen, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case ')':
					get();
					tokens.add(new Token(TokenType.RParen, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '&':
					get();
					tokens.add(new Token(TokenType.Amp, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '<':
					get();
					tokens.add(new Token(TokenType.LAngle, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case '>':
					get();
					tokens.add(new Token(TokenType.RAngle, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case ',':
					get();
					tokens.add(new Token(TokenType.Comma, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				case ':':
					get();
					if (peek == ':') {
						get();
						tokens.add(new Token(TokenType.ColonColon, tokenStartPosition,
								currentPosition - tokenStartPosition, syntaxHighlightingConstants.operatorTextAttribute,
								null));
						continue;
					}
					tokens.add(new Token(TokenType.Colon, tokenStartPosition, currentPosition - tokenStartPosition,
							syntaxHighlightingConstants.operatorTextAttribute, null));
					continue;
				}
				get();
				tokens.add(new Token(TokenType.Unknown, tokenStartPosition, currentPosition - tokenStartPosition, null,
						null));
			}
			tokens.add(new Token(TokenType.EndOfFile, currentPosition, 0, null, null));
			return tokens;
		}
	}

	private static class TokenSource {
		private final Iterator<Token> tokenIterator;
		public Token peek;

		private static boolean isSkippedTokenType(TokenType type) {
			switch (type) {
			case Amp:
			case CharacterClass:
			case CodeKeyword:
			case CodeSnippetStart:
			case Colon:
			case ColonColon:
			case Comma:
			case EMark:
			case EOFKeyword:
			case EndOfFile:
			case Equal:
			case FSlash:
			case FalseKeyword:
			case Identifier:
			case LAngle:
			case LParen:
			case NamespaceKeyword:
			case Plus:
			case QMark:
			case RAngle:
			case RParen:
			case Semicolon:
			case Star:
			case String:
			case TrueKeyword:
			case TypedefKeyword:
			case Unknown:
				return false;
			case BlockComment:
			case CodeSnippetBlockComment:
			case CodeSnippetChar:
			case CodeSnippetEnd:
			case CodeSnippetHeaderName:
			case CodeSnippetIdentifier:
			case CodeSnippetKeyword:
			case CodeSnippetLineComment:
			case CodeSnippetNumber:
			case CodeSnippetPunctuator:
			case CodeSnippetString:
			case CodeSnippetSubstitution:
			case CodeSnippetUnknown:
			case LineComment:
			case Whitespace:
				return true;
			}
			return false;
		}

		public TokenSource(Iterator<Token> tokenIterator) {
			this.tokenIterator = tokenIterator;
			peek = tokenIterator.next();
			while (isSkippedTokenType(peek.type))
				peek = tokenIterator.next();
		}

		public Token get() {
			Token retval = peek;
			if (!peek.isEOF())
				peek = tokenIterator.next();
			while (isSkippedTokenType(peek.type))
				peek = tokenIterator.next();
			return retval;
		}
	}

	private void skipTillGrammarStart(final TokenSource ts) {
		while (!ts.peek.isEOF() && ts.peek.type != TokenType.TypedefKeyword && ts.peek.type != TokenType.Identifier
				&& ts.peek.type != TokenType.CodeKeyword && ts.peek.type != TokenType.NamespaceKeyword) {
			if (ts.get().type == TokenType.Semicolon)
				return;
		}
	}

	private void parseType(final TokenSource ts) {
		Assert.isTrue(ts.peek.type == TokenType.TypedefKeyword);
		ts.get();
		if (ts.peek.type == TokenType.ColonColon) {
			ts.get().style = syntaxHighlightingConstants.codePunctuatorTextAttribute;
		}
		if (ts.peek.type != TokenType.Identifier) {
			skipTillGrammarStart(ts);
			return;
		}
		ts.get().style = syntaxHighlightingConstants.codeIdentifierTextAttribute;
		while (ts.peek.type == TokenType.ColonColon) {
			ts.get().style = syntaxHighlightingConstants.codePunctuatorTextAttribute;
			if (ts.peek.type != TokenType.Identifier) {
				skipTillGrammarStart(ts);
				return;
			}
			ts.get().style = syntaxHighlightingConstants.codeIdentifierTextAttribute;
		}
		if (ts.peek.type != TokenType.Identifier) {
			skipTillGrammarStart(ts);
			return;
		}
		ts.get().style = syntaxHighlightingConstants.typeNameTextAttribute;
		skipTillGrammarStart(ts);
	}

	private void parseTopLevelCodeSnippet(TokenSource ts) {
		Assert.isTrue(ts.peek.type == TokenType.CodeKeyword);
		ts.get();
		if (ts.peek.type != TokenType.Identifier) {
			skipTillGrammarStart(ts);
			return;
		}
		if (ts.peek.value.equals("license") || ts.peek.value.equals("header") || ts.peek.value.equals("source")
				|| ts.peek.value.equals("class")) {
			ts.peek.style = syntaxHighlightingConstants.keywordTextAttribute;
		}
		ts.get();
		skipTillGrammarStart(ts);
	}

	private boolean parsePrimaryExpression(TokenSource ts, boolean codeAllowed) {
		switch (ts.peek.type) {
		case LParen:
			ts.get();
			if (ts.peek.type == TokenType.RParen) {
				ts.get();
				return true;
			}
			if (!parseExpression(ts, codeAllowed))
				return false;
			if (ts.peek.type == TokenType.RParen)
				ts.get();
			return true;

		case Identifier:
			ts.get().style = syntaxHighlightingConstants.ruleNameTextAttribute;
			if (ts.peek.type == TokenType.LAngle) {
				do {
					ts.get();
					if (ts.peek.type == TokenType.TrueKeyword || ts.peek.type == TokenType.FalseKeyword) {
						ts.get();
					} else if (ts.peek.type == TokenType.Identifier) {
						ts.get().style = syntaxHighlightingConstants.templateVariableTextAttribute;
					} else if (ts.peek.type == TokenType.Comma) {
						continue;
					} else if (ts.peek.type == TokenType.RAngle) {
						break;
					} else {
						skipTillGrammarStart(ts);
						return false;
					}
				} while (ts.peek.type == TokenType.Comma);
				if (ts.peek.type == TokenType.RAngle) {
					ts.get();
				}
			}
			if (ts.peek.type == TokenType.Colon) {
				ts.get();
				if (ts.peek.type == TokenType.Identifier) {
					ts.get().style = codeAllowed ? syntaxHighlightingConstants.resultVariableTextAttribute
							: syntaxHighlightingConstants.identifierTextAttribute;
				}
			}
			return true;
		case EOFKeyword:
		case String:
			ts.get();
			return true;
		case CharacterClass:
			ts.get();
			if (ts.peek.type == TokenType.Colon) {
				ts.get();
				if (ts.peek.type == TokenType.Identifier) {
					ts.get().style = codeAllowed ? syntaxHighlightingConstants.resultVariableTextAttribute
							: syntaxHighlightingConstants.identifierTextAttribute;
				}
			}
			return true;
		case Amp: {
			Token ampToken = ts.get();
			boolean isCustomPredicate = ts.peek.type == TokenType.CodeSnippetStart;
			if (isCustomPredicate)
				ampToken.style = syntaxHighlightingConstants.codeTextAttribute;
			if (!parsePrimaryExpression(ts, codeAllowed))
				return false;
			return true;
		}
		case EMark:
			ts.get();
			if (!parsePrimaryExpression(ts, false))
				return false;
			return true;
		case CodeSnippetStart: {
			if (!codeAllowed)
				ts.peek.style = null;
			ts.get();
			return true;
		}
		default:
			skipTillGrammarStart(ts);
			return false;
		}
	}

	private boolean parseRepeatOptionalExpression(TokenSource ts, boolean codeAllowed) {
		if (!parsePrimaryExpression(ts, codeAllowed))
			return false;
		while (true) {
			if (ts.peek.type == TokenType.QMark) {
				ts.get();
			} else if (ts.peek.type == TokenType.Star) {
				ts.get();
			} else if (ts.peek.type == TokenType.Plus) {
				ts.get();
			} else {
				break;
			}
		}
		return true;
	}

	private boolean parseSequenceExpression(TokenSource ts, boolean codeAllowed) {
		if (!parseRepeatOptionalExpression(ts, codeAllowed))
			return false;
		while (true) {
			switch (ts.peek.type) {
			case Amp:
			case BlockComment:
			case CharacterClass:
			case CodeSnippetBlockComment:
			case CodeSnippetChar:
			case CodeSnippetEnd:
			case CodeSnippetHeaderName:
			case CodeSnippetIdentifier:
			case CodeSnippetKeyword:
			case CodeSnippetLineComment:
			case CodeSnippetNumber:
			case CodeSnippetPunctuator:
			case CodeSnippetStart:
			case CodeSnippetString:
			case CodeSnippetSubstitution:
			case CodeSnippetUnknown:
			case EMark:
			case EOFKeyword:
			case FalseKeyword:
			case Identifier:
			case LAngle:
			case LParen:
			case LineComment:
			case Plus:
			case QMark:
			case Star:
			case String:
			case TrueKeyword:
			case Whitespace:
				break;
			case CodeKeyword:
			case Colon:
			case ColonColon:
			case Comma:
			case EndOfFile:
			case Equal:
			case FSlash:
			case NamespaceKeyword:
			case RAngle:
			case RParen:
			case Semicolon:
			case TypedefKeyword:
			case Unknown:
				return true;
			}
			if (!parseRepeatOptionalExpression(ts, codeAllowed))
				return false;
		}
	}

	private boolean parseExpression(TokenSource ts, boolean codeAllowed) {
		if (!parseSequenceExpression(ts, codeAllowed))
			return false;
		while (ts.peek.type == TokenType.FSlash) {
			ts.get();
			if (!parseSequenceExpression(ts, codeAllowed))
				return false;
		}
		return true;
	}

	private void parseRule(TokenSource ts) {
		if (ts.peek.type != TokenType.Identifier) {
			skipTillGrammarStart(ts);
			return;
		}
		ts.get().style = syntaxHighlightingConstants.ruleNameTextAttribute;
		if (ts.peek.type == TokenType.LAngle) {
			do {
				ts.get();
				if (ts.peek.type == TokenType.Comma)
					continue;
				if (ts.peek.type == TokenType.RAngle)
					break;
				if (ts.peek.type != TokenType.Identifier) {
					skipTillGrammarStart(ts);
					return;
				}
				ts.get().style = syntaxHighlightingConstants.templateVariableTextAttribute;
				if (ts.peek.type != TokenType.Colon) {
					if (ts.peek.type == TokenType.Comma)
						continue;
					if (ts.peek.type == TokenType.RAngle)
						break;
				} else
					ts.get();
				if (ts.peek.type == TokenType.Comma)
					continue;
				if (ts.peek.type == TokenType.RAngle)
					break;
				if (ts.peek.type != TokenType.Identifier) {
					skipTillGrammarStart(ts);
					return;
				}
				ts.get().style = syntaxHighlightingConstants.typeNameTextAttribute;
			} while (ts.peek.type == TokenType.Comma);
			if (ts.peek.type == TokenType.RAngle)
				ts.get();
		}
		if (ts.peek.type == TokenType.Colon) {
			ts.get();
			if (ts.peek.type != TokenType.Identifier) {
				skipTillGrammarStart(ts);
				return;
			}
			ts.get().style = syntaxHighlightingConstants.typeNameTextAttribute;
		}
		if (ts.peek.type != TokenType.Equal) {
			skipTillGrammarStart(ts);
			return;
		}
		ts.get();
		parseExpression(ts, true);
		skipTillGrammarStart(ts);
		return;
	}

	private void parseGrammar(final TokenSource ts) {
		mainGrammarLoop: while (!ts.peek.isEOF()) {
			if (ts.peek.type == TokenType.TypedefKeyword) {
				parseType(ts);
			} else if (ts.peek.type == TokenType.CodeKeyword) {
				parseTopLevelCodeSnippet(ts);
			} else if (ts.peek.type == TokenType.NamespaceKeyword) {
				ts.get();
				if (ts.peek.type != TokenType.Identifier) {
					skipTillGrammarStart(ts);
					continue mainGrammarLoop;
				}
				ts.get().style = syntaxHighlightingConstants.codeIdentifierTextAttribute;
				while (ts.peek.type == TokenType.ColonColon) {
					ts.get().style = syntaxHighlightingConstants.codePunctuatorTextAttribute;
					if (ts.peek.type != TokenType.Identifier) {
						skipTillGrammarStart(ts);
						continue mainGrammarLoop;
					}
					ts.get().style = syntaxHighlightingConstants.codeIdentifierTextAttribute;
				}
				skipTillGrammarStart(ts);
			} else {
				parseRule(ts);
			}
		}
	}

	public List<Token> parse(IDocument document) {
		List<Token> retval = new Tokenizer(syntaxHighlightingConstants).tokenize(document);
		parseGrammar(new TokenSource(retval.iterator()));
		return retval;
	}
}
