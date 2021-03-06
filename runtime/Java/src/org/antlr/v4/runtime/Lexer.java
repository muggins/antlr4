/*
 [The "BSD license"]
  Copyright (c) 2011 Terence Parr
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:

  1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in the
     documentation and/or other materials provided with the distribution.
  3. The name of the author may not be used to endorse or promote products
     derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.antlr.v4.runtime;

import org.antlr.v4.runtime.atn.LexerATNSimulator;

import java.util.ArrayDeque;
import java.util.EmptyStackException;

/** A lexer is recognizer that draws input symbols from a character stream.
 *  lexer grammars result in a subclass of this object. A Lexer object
 *  uses simplified match() and error recovery mechanisms in the interest
 *  of speed.
 */
public abstract class Lexer extends Recognizer<Integer, LexerATNSimulator>
	implements TokenSource
{
	public static final int DEFAULT_MODE = 0;
	public static final int MORE = -2;
	public static final int SKIP = -3;

	public static final int DEFAULT_TOKEN_CHANNEL = Token.DEFAULT_CHANNEL;
	public static final int HIDDEN = Token.HIDDEN_CHANNEL;
	public static final int MIN_CHAR_VALUE = '\u0000';
	public static final int MAX_CHAR_VALUE = '\uFFFE';

	public CharStream _input;

	/** How to create token objects */
	protected TokenFactory<?> _factory = CommonTokenFactory.DEFAULT;

	/** The goal of all lexer rules/methods is to create a token object.
	 *  This is an instance variable as multiple rules may collaborate to
	 *  create a single token.  nextToken will return this object after
	 *  matching lexer rule(s).  If you subclass to allow multiple token
	 *  emissions, then set this to the last token to be matched or
	 *  something nonnull so that the auto token emit mechanism will not
	 *  emit another token.
	 */
	public Token _token;

	/** What character index in the stream did the current token start at?
	 *  Needed, for example, to get the text for current token.  Set at
	 *  the start of nextToken.
	 */
	public int _tokenStartCharIndex = -1;

	/** The line on which the first character of the token resides */
	public int _tokenStartLine;

	/** The character position of first character within the line */
	public int _tokenStartCharPositionInLine;

	/** Once we see EOF on char stream, next token will be EOF.
	 *  If you have DONE : EOF ; then you see DONE EOF.
	 */
	public boolean _hitEOF;

	/** The channel number for the current token */
	public int _channel;

	/** The token type for the current token */
	public int _type;

	public ArrayDeque<Integer> _modeStack;
	public int _mode = Lexer.DEFAULT_MODE;

	/** You can set the text for the current token to override what is in
	 *  the input char buffer.  Use setText() or can set this instance var.
	 */
	public String _text;

	public Lexer(CharStream input) {
		this._input = input;
	}

	public void reset() {
		// wack Lexer state variables
		if ( _input !=null ) {
			_input.seek(0); // rewind the input
		}
		_token = null;
		_type = Token.INVALID_TYPE;
		_channel = Token.DEFAULT_CHANNEL;
		_tokenStartCharIndex = -1;
		_tokenStartCharPositionInLine = -1;
		_tokenStartLine = -1;
		_text = null;

		_hitEOF = false;
		_mode = Lexer.DEFAULT_MODE;
		if (_modeStack != null) {
			_modeStack.clear();
		}

		getInterpreter().reset();
	}

	/** Return a token from this source; i.e., match a token on the char
	 *  stream.
	 */
	@Override
	public Token nextToken() {
		if (_hitEOF) return anEOF();

		outer:
		while (true) {
			_token = null;
			_channel = Token.DEFAULT_CHANNEL;
			_tokenStartCharIndex = _input.index();
			_tokenStartCharPositionInLine = getInterpreter().getCharPositionInLine();
			_tokenStartLine = getInterpreter().getLine();
			_text = null;
			do {
				_type = Token.INVALID_TYPE;
//				System.out.println("nextToken line "+tokenStartLine+" at "+((char)input.LA(1))+
//								   " in mode "+mode+
//								   " at index "+input.index());
				int ttype;
				try {
					ttype = getInterpreter().match(_input, _mode);
				}
				catch (LexerNoViableAltException e) {
					notifyListeners(e);		// report error
					recover(e);
					ttype = SKIP;
				}
				if ( _input.LA(1)==CharStream.EOF ) {
					_hitEOF = true;
				}
				if ( _type == Token.INVALID_TYPE ) _type = ttype;
				if ( _type ==SKIP ) {
					continue outer;
				}
			} while ( _type ==MORE );
			if ( _token ==null ) emit();
			return _token;
		}
	}

	/** Instruct the lexer to skip creating a token for current lexer rule
	 *  and look for another token.  nextToken() knows to keep looking when
	 *  a lexer rule finishes with token set to SKIP_TOKEN.  Recall that
	 *  if token==null at end of any token rule, it creates one for you
	 *  and emits it.
	 */
	public void skip() {
		_type = SKIP;
	}

	public void more() {
		_type = MORE;
	}

	public void mode(int m) {
		_mode = m;
	}

	public void pushMode(int m) {
		if ( LexerATNSimulator.debug ) System.out.println("pushMode "+m);
		if ( _modeStack ==null ) _modeStack = new ArrayDeque<Integer>();
		getInterpreter().tracePushMode(m);
		_modeStack.push(_mode);
		mode(m);
	}

	public int popMode() {
		if ( _modeStack ==null ) throw new EmptyStackException();
		if ( LexerATNSimulator.debug ) System.out.println("popMode back to "+ _modeStack.peek());
		getInterpreter().tracePopMode();
		mode( _modeStack.pop() );
		return _mode;
	}

	@Override
	public void setTokenFactory(TokenFactory<?> factory) {
		this._factory = factory;
	}

	/** Set the char stream and reset the lexer */
	@Override
	public void setInputStream(IntStream input) {
		this._input = null;
		reset();
		this._input = (CharStream)input;
	}

	@Override
	public String getSourceName() {
		return _input.getSourceName();
	}

	@Override
	public CharStream getInputStream() {
		return _input;
	}

	/** Currently does not support multiple emits per nextToken invocation
	 *  for efficiency reasons.  Subclass and override this method and
	 *  nextToken (to push tokens into a list and pull from that list rather
	 *  than a single variable as this implementation does).
	 */
	public void emit(Token token) {
		getInterpreter().traceEmit(token);
		//System.err.println("emit "+token);
		this._token = token;
	}

	/** The standard method called to automatically emit a token at the
	 *  outermost lexical rule.  The token object should point into the
	 *  char buffer start..stop.  If there is a text override in 'text',
	 *  use that to set the token's text.  Override this method to emit
	 *  custom Token objects or provide a new factory.
	 */
	public Token emit() {
		Token t = _factory.create(this, _type, _text, _channel, _tokenStartCharIndex, getCharIndex()-1,
								  _tokenStartLine, _tokenStartCharPositionInLine);
		emit(t);
		return t;
	}

	public Token anEOF() {
		int cpos = getCharPositionInLine();
		// The character position for EOF is one beyond the position of
		// the previous token's last character
		if ( _token !=null ) {
			int n = _token.getStopIndex() - _token.getStartIndex() + 1;
			cpos = _token.getCharPositionInLine()+n;
		}
		Token eof = _factory.create(this, Token.EOF, null, Token.DEFAULT_CHANNEL, _input.index(), _input.index()-1,
									getLine(), cpos);
		return eof;
	}

	@Override
	public int getLine() {
		return getInterpreter().getLine();
	}

	@Override
	public int getCharPositionInLine() {
		return getInterpreter().getCharPositionInLine();
	}

	/** What is the index of the current character of lookahead? */
	public int getCharIndex() {
		return _input.index();
	}

	/** Return the text matched so far for the current token or any
	 *  text override.
	 */
	public String getText() {
		if ( _text !=null ) {
			return _text;
		}
		return getInterpreter().getText(_input);
//		return ((CharStream)input).substring(tokenStartCharIndex,getCharIndex()-1);
	}

	/** Set the complete text of this token; it wipes any previous
	 *  changes to the text.
	 */
	public void setText(String text) {
		this._text = text;
	}

	public String[] getModeNames() {
		return null;
	}

	/** Used to print out token names like ID during debugging and
	 *  error reporting.  The generated parsers implement a method
	 *  that overrides this to point to their String[] tokenNames.
	 */
	@Override
	public String[] getTokenNames() {
		return null;
	}

	public void recover(LexerNoViableAltException e) {
		getInterpreter().consume(_input); // skip a char and try again
	}

	public void notifyListeners(LexerNoViableAltException e) {
		String msg = "token recognition error at: '"+
			_input.substring(_tokenStartCharIndex, _input.index())+"'";
		ANTLRErrorListener<Integer>[] listeners = getErrorListeners();
		if ( listeners.length == 0 ) {
			System.err.println("line "+ _tokenStartLine +":"+
							   _tokenStartCharPositionInLine +" "+
							   msg);
			return;
		}
		for (ANTLRErrorListener<Integer> pl : listeners) {
			pl.error(this, null, _tokenStartLine, _tokenStartCharPositionInLine, msg, e);
		}
	}

	public String getCharErrorDisplay(int c) {
		String s = String.valueOf((char)c);
		switch ( c ) {
			case Token.EOF :
				s = "<EOF>";
				break;
			case '\n' :
				s = "\\n";
				break;
			case '\t' :
				s = "\\t";
				break;
			case '\r' :
				s = "\\r";
				break;
		}
		return "'"+s+"'";
	}

	/** Lexers can normally match any char in it's vocabulary after matching
	 *  a token, so do the easy thing and just kill a character and hope
	 *  it all works out.  You can instead use the rule invocation stack
	 *  to do sophisticated error recovery if you are in a fragment rule.
	 */
	public void recover(RecognitionException re) {
		//System.out.println("consuming char "+(char)input.LA(1)+" during recovery");
		//re.printStackTrace();
		// TODO: Do we lose character or line position information?
		_input.consume();
	}
}
