/* Copyright (C) 2026 HoDoKu contributors. Licensed under GPL-3.0-or-later. */
// Modified by HoDoKu macOS contributors, 2026-10-10; see CHANGES.md.
package sudoku;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Private work-session ink representation. This is deliberately separate from
 * the shared .hrep format. Coordinate bits are preserved, without quantization.
 * Public only because XMLDecoder must invoke the static factory.
 */
public final class SessionInkCodec {
    private static final int VERSION = 1;
    private SessionInkCodec() {}

    static String metadata(DoodleStroke stroke) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeBoolean(stroke.getColor() != null);
            if (stroke.getColor() != null) out.writeInt(stroke.getColor().getRGB());
            out.writeInt(Float.floatToRawIntBits(stroke.getWidthFactor()));
            out.writeInt(stroke.getAnchorCell());
            out.writeInt(stroke.getAnchorDigit());
            out.writeInt(stroke.getCandidateMarkKind());
            out.writeInt(stroke.getThoughtGroup());
            out.writeBoolean(stroke.isConclusionOutlined());
            out.writeBoolean(stroke.isHypothesisStart());
            out.writeInt(stroke.getHypothesisConditionId());
            out.writeInt(stroke.getHypothesisGroupKind());
            out.writeInt(stroke.getConclusionSourceMask());
            out.writeBoolean(stroke.isProofOnly());
            out.writeInt(stroke.getDerivedSourceId());
            out.writeBoolean(stroke.isContradictionWitness());
            out.writeBoolean(stroke.isRefutationResult());
            out.close();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to encode session ink", impossible);
        }
    }

    static String encodeCoordinates(DoodleCoordinates coordinates) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(VERSION);
            out.writeInt(coordinates.size());
            for (int index = 0; index < coordinates.size(); index++) {
                out.writeLong(Double.doubleToRawLongBits(coordinates.x(index)));
                out.writeLong(Double.doubleToRawLongBits(coordinates.y(index)));
            }
            out.close();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException("Unable to encode session coordinates", impossible);
        }
    }

    /** One geometry dictionary entry; validates the byte length before allocating coordinates. */
    public static DoodleCoordinates coordinates(String encoded) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
            if (in.readUnsignedByte() != VERSION) throw new IOException("Unsupported session coordinate version");
            int count = in.readInt();
            if (count < 0 || (long) count * 16 != in.available())
                throw new IOException("Invalid session ink coordinate length");
            double[] points = new double[count * 2];
            for (int index = 0; index < points.length; index++) points[index] = Double.longBitsToDouble(in.readLong());
            return DoodleCoordinates.copyOf(points);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("Unable to decode session coordinates", failure);
        }
    }

    /** Reads one stroke's metadata; immutable geometry may be shared across independent histories. */
    public static DoodleStroke decode(String encoded, DoodleCoordinates coordinates) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readUnsignedByte() != VERSION) throw new IOException("Unsupported session ink version");
            Color color = in.readBoolean() ? new Color(in.readInt(), true) : null;
            DoodleStroke stroke = new DoodleStroke(color, Float.intBitsToFloat(in.readInt()));
            stroke.setAnchorCell(in.readInt());
            stroke.setAnchorDigit(in.readInt());
            stroke.setCandidateMarkKind(in.readInt());
            stroke.setThoughtGroup(in.readInt());
            stroke.setConclusionOutlined(in.readBoolean());
            stroke.setHypothesisStart(in.readBoolean());
            stroke.setHypothesisConditionId(in.readInt());
            stroke.setHypothesisGroupKind(in.readInt());
            stroke.setConclusionSourceMask(in.readInt());
            stroke.setProofOnly(in.readBoolean());
            stroke.setDerivedSourceId(in.readInt());
            stroke.setContradictionWitness(in.readBoolean());
            stroke.setRefutationResult(in.readBoolean());
            if (in.available() != 0) throw new IOException("Unexpected trailing session ink metadata");
            if (coordinates == null) throw new IOException("Missing session ink coordinates");
            stroke.replaceCoordinates(coordinates);
            return stroke;
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("Unable to decode session ink", failure);
        }
    }
}
