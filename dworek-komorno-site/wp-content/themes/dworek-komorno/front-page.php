<?php
/**
 * Strona glowna.
 *
 * @package Dworek_Komorno
 */

get_header();

$hero_image_id  = (int) get_theme_mod( 'dwk_hero_image', 0 );
$hero_image_url = $hero_image_id ? wp_get_attachment_image_url( $hero_image_id, 'dwk-hero' ) : '';
?>

<section class="hero" <?php if ( $hero_image_url ) : ?>style="background-image:url('<?php echo esc_url( $hero_image_url ); ?>');"<?php endif; ?>>
	<div class="hero-overlay"></div>
	<div class="container hero-inner">
		<p class="hero-eyebrow"><?php esc_html_e( 'Cukiernia internetowa', 'dworek-komorno' ); ?></p>
		<h1 class="hero-title"><?php echo esc_html( get_theme_mod( 'dwk_hero_title', __( 'Cukiernia Dworek Komorno', 'dworek-komorno' ) ) ); ?></h1>
		<p class="hero-subtitle"><?php echo esc_html( get_theme_mod( 'dwk_hero_subtitle', __( 'Torty z naturalnych składników, według tradycyjnych receptur. Zamów online z dostawą lub odbiorem w Dworku.', 'dworek-komorno' ) ) ); ?></p>
		<div class="hero-actions">
			<a class="btn btn-gold" href="<?php echo esc_url( home_url( '/stworz-wlasny-tort/' ) ); ?>"><?php esc_html_e( 'Stwórz własny tort', 'dworek-komorno' ); ?></a>
			<a class="btn btn-outline" href="<?php echo esc_url( home_url( '/oferta/' ) ); ?>"><?php esc_html_e( 'Zobacz ofertę', 'dworek-komorno' ); ?></a>
		</div>
	</div>
</section>

<section class="section">
	<div class="container">
		<h2 class="section-title"><?php esc_html_e( 'Jak zamówić tort?', 'dworek-komorno' ); ?></h2>
		<div class="steps-grid">
			<div class="step-card"><span class="step-num">1</span><h3><?php esc_html_e( 'Wybierz kształt i rozmiar', 'dworek-komorno' ); ?></h3><p><?php esc_html_e( 'Tort okrągły, prostokątny, kwadratowy lub w kształcie serca — od 8 do 35 porcji.', 'dworek-komorno' ); ?></p></div>
			<div class="step-card"><span class="step-num">2</span><h3><?php esc_html_e( 'Skomponuj smak i dekoracje', 'dworek-komorno' ); ?></h3><p><?php esc_html_e( 'Delikatne smaki śmietankowe i owocowe, wykończenie, dekoracje, własna grafika i napis.', 'dworek-komorno' ); ?></p></div>
			<div class="step-card"><span class="step-num">3</span><h3><?php esc_html_e( 'Odbierz lub zamów dostawę', 'dworek-komorno' ); ?></h3><p><?php esc_html_e( 'Odbiór osobisty w Dworku, dostawa do domu albo dostawa ekspresowa 24h.', 'dworek-komorno' ); ?></p></div>
		</div>
	</div>
</section>

<section class="section section-alt">
	<div class="container">
		<h2 class="section-title"><?php esc_html_e( 'Nasza oferta', 'dworek-komorno' ); ?></h2>
		<div class="cards-grid">
			<a class="offer-card" href="<?php echo esc_url( home_url( '/oferta/' ) ); ?>">
				<h3><?php esc_html_e( 'Torty okolicznościowe', 'dworek-komorno' ); ?></h3>
				<p><?php esc_html_e( 'Urodziny, imieniny, chrzest, komunia, rocznice.', 'dworek-komorno' ); ?></p>
			</a>
			<a class="offer-card" href="<?php echo esc_url( home_url( '/oferta/' ) ); ?>">
				<h3><?php esc_html_e( 'Torty weselne', 'dworek-komorno' ); ?></h3>
				<p><?php esc_html_e( 'Klasyczne torty piętrowe, naked cake, monoporcje i słodkie stoły.', 'dworek-komorno' ); ?></p>
			</a>
			<a class="offer-card" href="<?php echo esc_url( home_url( '/stworz-wlasny-tort/' ) ); ?>">
				<h3><?php esc_html_e( 'Tort z własną grafiką', 'dworek-komorno' ); ?></h3>
				<p><?php esc_html_e( 'Wgraj zdjęcie, dopasuj kadr i dodaj napis — wydrukujemy je na jadalnym opłatku.', 'dworek-komorno' ); ?></p>
			</a>
		</div>
	</div>
</section>

<section class="section">
	<div class="container two-col">
		<div>
			<h2 class="section-title section-title-left"><?php esc_html_e( 'Dostawa prosto do domu', 'dworek-komorno' ); ?></h2>
			<p><?php esc_html_e( 'Tort przewozimy w warunkach chłodniczych i dostarczamy pod wskazany adres. Wybrane torty dostarczamy ekspresowo — w ciągu 24 godzin od telefonicznego potwierdzenia zamówienia.', 'dworek-komorno' ); ?></p>
			<a class="btn btn-burgundy" href="<?php echo esc_url( home_url( '/dostawa/' ) ); ?>"><?php esc_html_e( 'Szczegóły dostawy', 'dworek-komorno' ); ?></a>
		</div>
		<div>
			<h2 class="section-title section-title-left"><?php esc_html_e( 'Punkty odbioru', 'dworek-komorno' ); ?></h2>
			<p><?php esc_html_e( 'Zamówienie możesz odebrać osobiście w cukierni lub w recepcji hotelu Dworek Komorno.', 'dworek-komorno' ); ?></p>
			<a class="btn btn-burgundy" href="<?php echo esc_url( home_url( '/punkty-odbioru/' ) ); ?>"><?php esc_html_e( 'Zobacz punkty odbioru', 'dworek-komorno' ); ?></a>
		</div>
	</div>
</section>

<?php
// Najnowsze wpisy (aktualnosci) jesli istnieja.
$news = new WP_Query( array( 'posts_per_page' => 3, 'ignore_sticky_posts' => true ) );
if ( $news->have_posts() ) :
?>
<section class="section section-alt">
	<div class="container">
		<h2 class="section-title"><?php esc_html_e( 'Aktualności', 'dworek-komorno' ); ?></h2>
		<div class="cards-grid">
			<?php while ( $news->have_posts() ) : $news->the_post(); ?>
				<article class="news-card">
					<?php if ( has_post_thumbnail() ) : ?>
						<a href="<?php the_permalink(); ?>"><?php the_post_thumbnail( 'dwk-card' ); ?></a>
					<?php endif; ?>
					<h3><a href="<?php the_permalink(); ?>"><?php the_title(); ?></a></h3>
					<p><?php echo esc_html( wp_trim_words( get_the_excerpt(), 20 ) ); ?></p>
				</article>
			<?php endwhile; wp_reset_postdata(); ?>
		</div>
	</div>
</section>
<?php endif; ?>

<?php get_footer(); ?>
