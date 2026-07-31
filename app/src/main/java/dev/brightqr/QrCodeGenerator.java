package dev.brightqr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

final class QrCodeGenerator {
    private QrCodeGenerator() {
    }

    static BitMatrix encode(String rawPayload) throws WriterException {
        String payload = rawPayload == null ? "" : rawPayload.trim();
        if (payload.isEmpty()) {
            throw new IllegalArgumentException("Enter text or a URL first.");
        }

        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 4);

        // Asking for 1x1 returns ZXing's minimum integral module matrix, including
        // the quiet zone. The GL renderer scales it with nearest-neighbor sampling.
        return new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 1, 1, hints);
    }
}
