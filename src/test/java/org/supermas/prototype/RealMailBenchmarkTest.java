package org.supermas.prototype;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealMailBenchmarkTest {
    @Test
    void nameWordsComeFromDisplayNamesAndAddressesWithoutDomainWords() {
        String header = "From: chris.germany@enron.com\nTo: sandra.dial@enron.com, Jeff W Westover\n\tother.person@enron.com\n"
                + "X-From: Chris Germany\nX-To: Sandra Dial, Jeff W Westover/HOU/ECT@ECT\nSubject: CES";
        assertEquals(Set.of("chris", "germany"), RealMailBenchmark.names(header, "From", "X-From"));
        Set<String> to = RealMailBenchmark.names(header, "To", "X-To");
        assertTrue(to.containsAll(Set.of("sandra", "dial", "jeff", "westover", "other", "person")), to.toString());
        assertTrue(to.stream().noneMatch(Set.of("enron", "com", "hou", "ect")::contains), to.toString());
    }
}
