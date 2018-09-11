package edu.washington.cs.mut.testrunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;

public class JUnitListener implements JUnitResultFormatter {

  public static final String TEST_CLASS_NAME_SEPARATOR = "::";

  private boolean hasFailed;

  private long startTime;

  private String stackTrace;

  private PrintStream ps;

  {
    try {
      this.ps = new PrintStream(new FileOutputStream(System.getProperty("TESTS_OUTFILE", "tests.txt"), true), true);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 
   */
  public JUnitListener() {
    super();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void startTest(Test test) {
    this.hasFailed = false;
    this.startTime = System.nanoTime();
    this.stackTrace = "";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void endTest(Test test) {
    ps.println(this.getName(test) + "," + String.valueOf(System.nanoTime() - this.startTime) +
               "," + (this.hasFailed ? "fail" : "pass") + "," + this.stackTrace);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void startTestSuite(JUnitTest suite) throws BuildException {
    // no-op
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void endTestSuite(JUnitTest suite) throws BuildException {
    // no-op
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addFailure(Test test, AssertionFailedError assertionFailedError) {
    this.hasFailed = true;
    this.stackTrace = this.traceToString(assertionFailedError);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void addError(Test test, Throwable throwable) {
    this.hasFailed = true;
    this.stackTrace = this.traceToString(throwable);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setOutput(OutputStream out) {
    // nop
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSystemError(String err) {
    // no-op
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void setSystemOutput(String out) {
    // no-op
  }

  private String getName(Test test) {
    String className = null;
    String methodName = null;

    {
      Pattern regexp = Pattern.compile("(.*)\\((.*)\\)");
      Matcher match = regexp.matcher(test.toString());
      if (match.matches()) {
        className = match.group(2);
        methodName = match.group(1);
      }
    }
    {
      // for some weird reason this format is used for Timeout in Junit4
      Pattern regexp = Pattern.compile("(.*):(.*)");
      Matcher match = regexp.matcher(test.toString());
      if (match.matches()) {
        className = match.group(1);
        methodName = match.group(2);
      }
    }

    return className + TEST_CLASS_NAME_SEPARATOR + methodName;
  }

  /**
   * Converts the stack trace of a throwable exception to string.
   * 
   * @param exception The exception thrown.
   * @return A string of the stack trace of a throwable exception.
   */
  private final String traceToString(Throwable exception) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    exception.printStackTrace(writer);
    String stackTraceStr = stringWriter.toString();

    // Converts a multi-line formatted stack trace in one line. It also truncates
    // the stack trace to a certain amount of bytes (i.e., unsigned short).
    if (stackTraceStr == null || stackTraceStr.isEmpty()) {
      return "";
    }

    String normalizedString =
        // kill newlines and surrounding space
        stackTraceStr.replaceAll(" *\r?\n[ \t]*", " ")
            // strip whitespace
            .replaceAll("^[ \t\r\n]*|[ \t\r\n]*$", "");

    // the stack trace of a, for example, java.lang.StackOverflowError could be extremely long, in
    // such cases the stack trace is truncated to a certain amount of bytes to keep it short

    Charset utf8 = Charset.forName(StandardCharsets.UTF_8.name());
    int maxNumBytes = Short.MAX_VALUE * 2; // unsigned short

    byte[] normalizedStringBytes = normalizedString.getBytes(utf8);
    if (normalizedStringBytes.length <= maxNumBytes) {
      return normalizedString;
    }

    // ensure truncation by having a byte buffer = maxNumBytes
    ByteBuffer byteBuffer = ByteBuffer.wrap(normalizedStringBytes, 0, maxNumBytes);
    CharBuffer charBuffer = CharBuffer.allocate(normalizedString.length());

    CharsetDecoder decoder = utf8.newDecoder();
    decoder.onMalformedInput(CodingErrorAction.IGNORE); // ignore any incomplete character
    decoder.decode(byteBuffer, charBuffer, true);
    decoder.flush(charBuffer);

    return new String(charBuffer.array(), 0, charBuffer.position());
  }
}
