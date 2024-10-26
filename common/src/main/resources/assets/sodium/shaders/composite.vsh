#version 150 core

const vec2[] VERTICES = vec2[](
    vec2(-1.0f, -1.0f),
    vec2( 3.0f, -1.0f),
    vec2(-1.0f,  3.0f)
);

out vec2 v_TexCoord;

void main() {
    gl_Position = vec4(VERTICES[gl_VertexID], 0.0f, 1.0f);

    v_TexCoord = (0.5f * gl_Position.xy) + vec2(0.5);
}