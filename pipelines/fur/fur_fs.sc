$input v_wpos, v_view, v_normal, v_tangent, v_bitangent, v_texcoord0, v_common2

#include "common.sh"

SAMPLER2D(u_texColor, 0);
#ifdef NORMAL_MAPPING
	SAMPLER2D(u_texNormal, 1);
#endif
#ifndef SHADOW
	SAMPLER2D(u_texShadowmap, 15);
#endif

uniform vec4 u_lightPosRadius;
uniform vec4 u_lightRgbAttenuation;
uniform vec4 u_ambientColor;
uniform vec4 u_lightDirFov; 
uniform mat4 u_shadowmapMatrices[4];
uniform vec4 u_fogColorDensity; 
uniform vec4 u_lightSpecular;
uniform vec4 u_materialColor;
uniform vec4 u_attenuationParams;
uniform vec4 u_fogParams;
uniform vec4 u_layer;
uniform vec4 u_alphaMultiplier;
uniform vec4 u_darkening;
uniform vec4 u_roughnessMetallic;


void main()
{     
	vec4 color = texture2D(u_texColor, v_texcoord0);
	color.xyz *= u_materialColor.rgb;
	#ifdef DEFERRED
		gl_FragData[0].rgb = color.rgb;
		gl_FragData[0].w = u_roughnessMetallic.x;
		mat3 tbn = mat3(
			normalize(v_tangent),
			normalize(v_normal),
			normalize(v_bitangent)
			);
		tbn = transpose(tbn);
		vec3 normal;
		#ifdef NORMAL_MAPPING
			normal.xzy = texture2D(u_texNormal, v_texcoord0).xyz * 2.0 - 1.0;
			normal = normalize(mul(tbn, normal));
		#else
			normal = normalize(v_normal.xyz);
		#endif
		gl_FragData[1].xyz = (normal + 1) * 0.5; // todo: store only xz 
		gl_FragData[1].w = u_roughnessMetallic.y;
		gl_FragData[2] = vec4(0, 0, 0, 1);
	#else
		#ifdef SHADOW
			float depth = v_common2.z/v_common2.w;
			gl_FragColor = vec4_splat(depth);
		#else
			mat3 tbn = mat3(
						normalize(v_tangent),
						normalize(v_normal),
						normalize(v_bitangent)
						);
			tbn = transpose(tbn);
						
			vec3 wnormal;
			#ifdef NORMAL_MAPPING
				wnormal.xz = texture2D(u_texNormal, v_texcoord0).xy * 2.0 - 1.0;
				wnormal.y = sqrt(1.0 - dot(wnormal.xz, wnormal.xz) );
				wnormal = mul(tbn, wnormal);
			#else
				wnormal = normalize(v_normal.xyz);
			#endif
			
			vec3 view = normalize(v_view);

			vec3 diffuse;
			diffuse = shadeDirectionalLight(u_lightDirFov.xyz
				, view
				, u_lightRgbAttenuation.rgb
				, u_lightSpecular.rgb
				, wnormal
				, u_materialColor
				, vec3(0, 0, 0));
			diffuse = diffuse.xyz * color.rgb;
			float ndotl = -dot(wnormal, u_lightDirFov.xyz);
			//diffuse = diffuse * directionalLightShadow(u_texShadowmap, u_shadowmapMatrices, vec4(v_wpos, 1.0), ndotl); 

			#if defined MAIN || defined FUR
				vec3 ambient = u_ambientColor.rgb * color.rgb;
			#else
				vec3 ambient = vec3(0, 0, 0);
			#endif  

			vec4 camera_wpos = mul(u_invView, vec4(0, 0, 0, 1.0));
			float fog_factor = getFogFactor(camera_wpos.xyz / camera_wpos.w, u_fogColorDensity.w, v_wpos.xyz, u_fogParams);
			gl_FragColor.xyz = mix(diffuse + ambient, u_fogColorDensity.rgb, fog_factor);
			gl_FragColor.rgb *= mix(u_darkening.x, 1, u_layer.x);
			float alpha = clamp(color.a * u_alphaMultiplier.x - u_layer.x, 0, 1);
			#ifdef ALPHA_CUTOUT
				if(alpha < u_alphaRef) discard;
			#endif

			gl_FragColor.a = alpha;
		#endif       
	#endif		
}
