package com.nafim.ar_tryon_view.ar;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import java.nio.FloatBuffer;
import java.util.Collection;

public final class PlaneRenderer {
    private int program;
    private int positionHandle;
    private int mvpHandle;
    private int colorHandle;

    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    public void createOnGlThread() {
        String vertex =
                "uniform mat4 u_MVP;\n" +
                "attribute vec3 a_Position;\n" +
                "void main(){ gl_Position=u_MVP*vec4(a_Position,1.0); }";
        String fragment =
                "precision mediump float;\n" +
                "uniform vec4 u_Color;\n" +
                "void main(){ gl_FragColor=u_Color; }";

        program = GlUtil.createProgram(vertex, fragment);
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position");
        mvpHandle = GLES20.glGetUniformLocation(program, "u_MVP");
        colorHandle = GLES20.glGetUniformLocation(program, "u_Color");
    }

    public void draw(Session session, float[] view, float[] projection) {
        Collection<Plane> planes = session.getAllTrackables(Plane.class);
        GLES20.glUseProgram(program);
        GLES20.glLineWidth(4f);

        for (Plane plane : planes) {
            if (plane.getTrackingState() != TrackingState.TRACKING) continue;
            if (plane.getSubsumedBy() != null) continue;
            if (plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING) continue;

            FloatBuffer polygon = plane.getPolygon();
            polygon.position(0);
            int pointCount = polygon.remaining() / 2;
            if (pointCount < 3) continue;

            float[] vertices = new float[pointCount * 3];
            int i = 0;
            while (polygon.hasRemaining()) {
                float x = polygon.get();
                float z = polygon.get();
                vertices[i++] = x;
                vertices[i++] = 0f;
                vertices[i++] = z;
            }

            FloatBuffer buffer = GlUtil.floatBuffer(vertices);
            plane.getCenterPose().toMatrix(modelMatrix, 0);
            Matrix.multiplyMM(modelViewMatrix, 0, view, 0, modelMatrix, 0);
            Matrix.multiplyMM(mvpMatrix, 0, projection, 0, modelViewMatrix, 0);

            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);
            GLES20.glUniform4f(colorHandle, 0.1f, 1f, 0.2f, 1f);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT,
                    false, 0, buffer);
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, pointCount);
            GLES20.glDisableVertexAttribArray(positionHandle);
        }
    }
}
