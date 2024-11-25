#version 150
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;

// Custom cloud fog algorithm by Balint, for use in Sodium
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec4 pos = ModelViewMat * vec4(Position.x, 0.0, Position.z, 1.0);
    vertexDistance = length(pos.xyz);
    vertexColor = Color;
}
