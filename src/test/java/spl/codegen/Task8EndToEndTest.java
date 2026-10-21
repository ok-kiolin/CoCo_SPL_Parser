package spl.codegen;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spl.Main;
import static org.junit.jupiter.api.Assertions.*;

/** Default public driver -> semantic results -> target source -> executable output. */
class Task8EndToEndTest {
    @TempDir Path directory;

    private String execute(String source) throws Exception {
        Path input = directory.resolve("task8 program.spl");
        Files.writeString(input, source);
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int code = Main.run(new String[]{input.toString()},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors));
        assertEquals(Main.OK, code, errors.toString());
        return CppTestSupport.execute(directory, Files.readString(directory.resolve("target.cpp")));
    }

    @Test void threeLevelCapturesAreReboundForEachOuterCall() throws Exception {
        assertEquals("16:18:2", execute("#global : num #outer ( #p ) { #x : "
                + "num #middle ( #q ) { #y : num #inner ( #r ) { : : "
                + "#x = add ( #x #r ) ; #y = add ( #y 1 ) ; #global = add ( #global 1 ) ; "
                + "return ( add ( #x #y ) ) } : #y = #q ; return ( #inner ( 2 ) ) } "
                + ": #x = #p ; return ( #middle ( 10 ) ) } : #global = 0 ; "
                + "print ( #outer ( 3 ) ) ; print \":\" ; print ( #outer ( 5 ) ) ; "
                + "print \":\" ; print ( #global ) ;"));
    }

    @Test void repeatedFunctionWithLoopAndReturnCallRunsThroughDriver() throws Exception {
        assertEquals("6:20", execute(": num #sum ( #n ) { #i #total : "
                + "num #double ( #v ) { : : return ( mul ( #v 2 ) ) } : #i = 0 ; #total = 0 ; "
                + "while lesser ( #i #n ) do { #total = add ( #total #i ) ; #i = add ( #i 1 ) ; } ; "
                + "return ( #double ( #total ) ) } : print ( #sum ( 3 ) ) ; "
                + "print \":\" ; print ( #sum ( 5 ) ) ;"));
    }

    @Test void floatModeSurvivesCallsAndLoopComparisons() throws Exception {
        assertEquals("2.5:3", execute("#i : num #half ( #n ) { : : return ( div ( #n 2 ) ) } "
                + ": print ( #half ( 5 ) ) ; print \":\" ; #i = 0 ; "
                + "while lesser ( #i #half ( 5 ) ) do { #i = add ( #i 1 ) ; } ; print ( #i ) ;"));
    }
}
