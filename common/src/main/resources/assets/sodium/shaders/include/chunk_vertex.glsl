// The position of the vertex around the model origin
vec3 _vert_position;

// The block texture coordinate of the vertex
vec2 _vert_tex_coord;
vec2 _vert_tex_origin;

// The light texture coordinate of the vertex
vec2 _vert_light;

// The color of the vertex
vec4 _vert_color;

// The index of the draw command which this vertex belongs to
uint _draw_id;

#ifdef USE_VERTEX_COMPRESSION
const uint POSITION_BITS        = 20u;
const uint POSITION_MAX_COORD   = 1u << POSITION_BITS;
const uint POSITION_MAX_VALUE   = POSITION_MAX_COORD - 1u;

const float VERTEX_SCALE = 32.0 / float(POSITION_MAX_COORD);
const float VERTEX_OFFSET = -8.0;

in uvec2 a_Position;
in vec4 a_Color;
in vec2 a_TexCoord;
in vec2 a_TexOrigin;
in uvec4 a_LightAndData;

uvec3 _deinterleave_u20x3(uvec2 data) {
    uvec3 hi = (uvec3(data.x) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    uvec3 lo = (uvec3(data.y) >> uvec3(0u, 10u, 20u)) & 0x3FFu;

    return (hi << 10u) | lo;
}

void _vert_init() {
    _vert_position = (_deinterleave_u20x3(a_Position) * VERTEX_SCALE) + VERTEX_OFFSET;
    _vert_color = a_Color;

    _vert_tex_coord = a_TexCoord / float(1 << 15);
    _vert_tex_origin = a_TexOrigin;

    _vert_light = vec2(a_LightAndData.xy) / vec2(256.0);

    _draw_id = a_LightAndData[3];
}

#else
#error "Vertex compression must be enabled"
#endif