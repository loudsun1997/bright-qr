package dev.brightqr;

import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class QrCodeGeneratorTest {
    @Test(expected = IllegalArgumentException.class)
    public void rejectsBlankPayload() throws WriterException {
        QrCodeGenerator.encode("   ");
    }

    @Test
    public void generatedCodeContainsBlackModulesAndWhiteQuietZone() throws WriterException {
        BitMatrix matrix = QrCodeGenerator.encode("https://example.com");

        assertFalse(matrix.get(0, 0));
        boolean foundBlack = false;
        for (int y = 0; y < matrix.getHeight() && !foundBlack; y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) {
                    foundBlack = true;
                    break;
                }
            }
        }
        assertTrue(foundBlack);
    }
}
