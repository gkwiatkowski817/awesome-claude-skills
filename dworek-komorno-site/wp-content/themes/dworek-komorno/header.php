<?php
/**
 * Naglowek strony.
 *
 * @package Dworek_Komorno
 */
?>
<!doctype html>
<html <?php language_attributes(); ?>>
<head>
	<meta charset="<?php bloginfo( 'charset' ); ?>">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<?php wp_head(); ?>
</head>
<body <?php body_class(); ?>>
<?php wp_body_open(); ?>

<div class="topbar">
	<div class="container topbar-inner">
		<span class="topbar-item">&#9742; <a href="tel:<?php echo esc_attr( preg_replace( '/\s+/', '', dwk_contact( 'phone' ) ) ); ?>"><?php echo esc_html( dwk_contact( 'phone' ) ); ?></a></span>
		<span class="topbar-item">&#9993; <a href="mailto:<?php echo esc_attr( dwk_contact( 'email' ) ); ?>"><?php echo esc_html( dwk_contact( 'email' ) ); ?></a></span>
		<span class="topbar-item topbar-hours"><?php echo esc_html( dwk_contact( 'hours' ) ); ?></span>
	</div>
</div>

<header class="site-header">
	<div class="container header-inner">
		<div class="site-branding">
			<?php if ( has_custom_logo() ) : ?>
				<?php the_custom_logo(); ?>
			<?php else : ?>
				<a class="site-title" href="<?php echo esc_url( home_url( '/' ) ); ?>">
					<span class="site-title-main"><?php bloginfo( 'name' ); ?></span>
					<span class="site-title-tagline"><?php bloginfo( 'description' ); ?></span>
				</a>
			<?php endif; ?>
		</div>

		<button class="menu-toggle" aria-controls="primary-menu" aria-expanded="false">
			<span class="menu-toggle-bar"></span><span class="menu-toggle-bar"></span><span class="menu-toggle-bar"></span>
			<span class="screen-reader-text"><?php esc_html_e( 'Menu', 'dworek-komorno' ); ?></span>
		</button>

		<nav class="main-nav" aria-label="<?php esc_attr_e( 'Menu glowne', 'dworek-komorno' ); ?>">
			<?php
			wp_nav_menu( array(
				'theme_location' => 'primary',
				'menu_id'        => 'primary-menu',
				'container'      => false,
				'fallback_cb'    => 'wp_page_menu',
			) );
			?>
		</nav>

		<a class="btn btn-gold header-cta" href="<?php echo esc_url( home_url( '/stworz-wlasny-tort/' ) ); ?>"><?php esc_html_e( 'Stwórz własny tort', 'dworek-komorno' ); ?></a>
	</div>
</header>
