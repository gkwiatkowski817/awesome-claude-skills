<?php
/**
 * Dworek Komorno Cukiernia - funkcje motywu.
 *
 * @package Dworek_Komorno
 */

defined( 'ABSPATH' ) || exit;

define( 'DWK_THEME_VERSION', '1.0.0' );

require_once get_template_directory() . '/inc/customizer.php';
require_once get_template_directory() . '/inc/demo-setup.php';

/**
 * Konfiguracja motywu.
 */
function dwk_theme_setup() {
	load_theme_textdomain( 'dworek-komorno', get_template_directory() . '/languages' );

	add_theme_support( 'title-tag' );
	add_theme_support( 'post-thumbnails' );
	add_theme_support( 'custom-logo', array(
		'height'      => 90,
		'width'       => 240,
		'flex-height' => true,
		'flex-width'  => true,
	) );
	add_theme_support( 'html5', array( 'search-form', 'comment-form', 'comment-list', 'gallery', 'caption', 'style', 'script' ) );
	add_theme_support( 'automatic-feed-links' );
	add_theme_support( 'responsive-embeds' );
	add_theme_support( 'align-wide' );

	// Wsparcie dla WooCommerce (opcjonalny katalog produktow / sklep).
	add_theme_support( 'woocommerce' );
	add_theme_support( 'wc-product-gallery-zoom' );
	add_theme_support( 'wc-product-gallery-lightbox' );
	add_theme_support( 'wc-product-gallery-slider' );

	register_nav_menus( array(
		'primary' => __( 'Menu glowne', 'dworek-komorno' ),
		'footer'  => __( 'Menu w stopce', 'dworek-komorno' ),
	) );

	add_image_size( 'dwk-card', 480, 360, true );
	add_image_size( 'dwk-hero', 1600, 700, true );
}
add_action( 'after_setup_theme', 'dwk_theme_setup' );

/**
 * Style i skrypty.
 */
function dwk_enqueue_assets() {
	wp_enqueue_style(
		'dwk-fonts',
		'https://fonts.googleapis.com/css2?family=Playfair+Display:ital,wght@0,400;0,600;0,700;1,400&family=Lato:wght@300;400;700&display=swap',
		array(),
		null
	);
	wp_enqueue_style( 'dwk-main', get_template_directory_uri() . '/assets/css/main.css', array( 'dwk-fonts' ), DWK_THEME_VERSION );
	wp_enqueue_script( 'dwk-main', get_template_directory_uri() . '/assets/js/main.js', array(), DWK_THEME_VERSION, true );

	// Zmienne CSS z customizera (kolory marki).
	$css = sprintf(
		':root{--dwk-cream:%s;--dwk-ink:%s;--dwk-gold:%s;--dwk-burgundy:%s;}',
		esc_html( get_theme_mod( 'dwk_color_cream', '#faf6ef' ) ),
		esc_html( get_theme_mod( 'dwk_color_ink', '#3a2a1d' ) ),
		esc_html( get_theme_mod( 'dwk_color_gold', '#b08d57' ) ),
		esc_html( get_theme_mod( 'dwk_color_burgundy', '#7a2e2e' ) )
	);
	wp_add_inline_style( 'dwk-main', $css );
}
add_action( 'wp_enqueue_scripts', 'dwk_enqueue_assets' );

/**
 * Obszary widgetow.
 */
function dwk_widgets_init() {
	register_sidebar( array(
		'name'          => __( 'Stopka - kolumna 1', 'dworek-komorno' ),
		'id'            => 'footer-1',
		'before_widget' => '<div class="footer-widget">',
		'after_widget'  => '</div>',
		'before_title'  => '<h4 class="footer-widget-title">',
		'after_title'   => '</h4>',
	) );
	register_sidebar( array(
		'name'          => __( 'Stopka - kolumna 2', 'dworek-komorno' ),
		'id'            => 'footer-2',
		'before_widget' => '<div class="footer-widget">',
		'after_widget'  => '</div>',
		'before_title'  => '<h4 class="footer-widget-title">',
		'after_title'   => '</h4>',
	) );
}
add_action( 'widgets_init', 'dwk_widgets_init' );

/**
 * Dane kontaktowe uzywane w naglowku/stopce.
 */
function dwk_contact( $key ) {
	$defaults = array(
		'phone'   => '+48 77 403 39 19',
		'email'   => 'cukiernia@dworekkomorno.eu',
		'address' => 'ul. Harcerska 85, Komorno',
		'hours'   => 'wt.-pt. 13:00-21:00, sob.-niedz. 12:00-21:00',
	);
	return get_theme_mod( 'dwk_contact_' . $key, isset( $defaults[ $key ] ) ? $defaults[ $key ] : '' );
}
