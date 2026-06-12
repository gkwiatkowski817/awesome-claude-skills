<?php
/**
 * Opcje customizera: kolory marki i dane kontaktowe.
 *
 * @package Dworek_Komorno
 */

defined( 'ABSPATH' ) || exit;

function dwk_customize_register( $wp_customize ) {
	// --- Sekcja: kolory marki ---
	$wp_customize->add_section( 'dwk_brand', array(
		'title'    => __( 'Dworek - kolory marki', 'dworek-komorno' ),
		'priority' => 30,
	) );

	$colors = array(
		'dwk_color_cream'    => array( __( 'Tlo (krem)', 'dworek-komorno' ), '#faf6ef' ),
		'dwk_color_ink'      => array( __( 'Tekst (ciemny braz)', 'dworek-komorno' ), '#3a2a1d' ),
		'dwk_color_gold'     => array( __( 'Akcent (zloto)', 'dworek-komorno' ), '#b08d57' ),
		'dwk_color_burgundy' => array( __( 'Akcent dodatkowy (bordo)', 'dworek-komorno' ), '#7a2e2e' ),
	);
	foreach ( $colors as $id => $cfg ) {
		$wp_customize->add_setting( $id, array(
			'default'           => $cfg[1],
			'sanitize_callback' => 'sanitize_hex_color',
		) );
		$wp_customize->add_control( new WP_Customize_Color_Control( $wp_customize, $id, array(
			'label'   => $cfg[0],
			'section' => 'dwk_brand',
		) ) );
	}

	// --- Sekcja: dane kontaktowe ---
	$wp_customize->add_section( 'dwk_contact', array(
		'title'    => __( 'Dworek - dane kontaktowe', 'dworek-komorno' ),
		'priority' => 31,
	) );

	$fields = array(
		'dwk_contact_phone'   => __( 'Telefon', 'dworek-komorno' ),
		'dwk_contact_email'   => __( 'E-mail', 'dworek-komorno' ),
		'dwk_contact_address' => __( 'Adres', 'dworek-komorno' ),
		'dwk_contact_hours'   => __( 'Godziny otwarcia', 'dworek-komorno' ),
	);
	foreach ( $fields as $id => $label ) {
		$wp_customize->add_setting( $id, array(
			'default'           => '',
			'sanitize_callback' => 'sanitize_text_field',
		) );
		$wp_customize->add_control( $id, array(
			'label'   => $label,
			'section' => 'dwk_contact',
			'type'    => 'text',
		) );
	}

	// --- Sekcja: strona glowna ---
	$wp_customize->add_section( 'dwk_hero', array(
		'title'    => __( 'Dworek - sekcja powitalna', 'dworek-komorno' ),
		'priority' => 32,
	) );
	$wp_customize->add_setting( 'dwk_hero_title', array(
		'default'           => __( 'Cukiernia Dworek Komorno', 'dworek-komorno' ),
		'sanitize_callback' => 'sanitize_text_field',
	) );
	$wp_customize->add_control( 'dwk_hero_title', array(
		'label'   => __( 'Tytul powitalny', 'dworek-komorno' ),
		'section' => 'dwk_hero',
		'type'    => 'text',
	) );
	$wp_customize->add_setting( 'dwk_hero_subtitle', array(
		'default'           => __( 'Torty z naturalnych skladnikow, wedlug tradycyjnych receptur. Zamow online z dostawa lub odbiorem w Dworku.', 'dworek-komorno' ),
		'sanitize_callback' => 'sanitize_text_field',
	) );
	$wp_customize->add_control( 'dwk_hero_subtitle', array(
		'label'   => __( 'Podtytul powitalny', 'dworek-komorno' ),
		'section' => 'dwk_hero',
		'type'    => 'textarea',
	) );
	$wp_customize->add_setting( 'dwk_hero_image', array(
		'default'           => '',
		'sanitize_callback' => 'absint',
	) );
	$wp_customize->add_control( new WP_Customize_Media_Control( $wp_customize, 'dwk_hero_image', array(
		'label'     => __( 'Zdjecie w tle (hero)', 'dworek-komorno' ),
		'section'   => 'dwk_hero',
		'mime_type' => 'image',
	) ) );
}
add_action( 'customize_register', 'dwk_customize_register' );
